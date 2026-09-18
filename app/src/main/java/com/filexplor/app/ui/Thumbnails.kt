package com.filexplor.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.FileKind
import com.filexplor.app.data.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Small previews for the files a list can show one of.
 *
 * Pictures, video frames and the icon inside an APK — the three kinds where the
 * file's own contents say more than any glyph could. Everything else keeps the
 * typed icon, which is the right answer rather than a fallback: there is no
 * useful thumbnail of a spreadsheet.
 *
 * Local files only, deliberately. Thumbnailing a server folder means
 * downloading every picture in it to draw one screen — on a phone connection
 * that is a surprise the user did not ask for, and it would happen again on
 * every scroll.
 */
object Thumbnails {

    /**
     * An eighth of the heap, measured in bytes rather than entries.
     *
     * Counting entries is the version that gets this wrong: a list of camera
     * photographs and a list of icons hold wildly different amounts of memory
     * for the same count, and the cache has to be bounded by the thing that
     * actually runs out.
     */
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Pictures past this size are skipped rather than decoded.
     *
     * Applies to bitmaps only. It was originally applied to every kind, which
     * quietly dropped the icon off any APK over the limit — and a game or a
     * large app is routinely past it. Reading an APK's icon parses its manifest
     * and pulls one small drawable; the size of the archive around that has
     * nothing to do with what it costs.
     */
    private const val MAX_BITMAP_BYTES = 80L * 1024 * 1024

    /** Whether this entry is one a preview can be made of. */
    fun canPreview(item: FileItem, location: Location): Boolean {
        if (location != Location.Device || item.isDirectory) return false
        return when (item.kind) {
            FileKind.APK -> true
            FileKind.IMAGE, FileKind.VIDEO -> item.size <= MAX_BITMAP_BYTES
            else -> false
        }
    }

    /**
     * Keyed on more than the path.
     *
     * A file that is replaced keeps its name, so a path-only key serves the old
     * picture from cache forever. Size and timestamp together change whenever
     * the contents do.
     */
    private fun keyOf(item: FileItem, pixels: Int) =
        "${item.path}|${item.size}|${item.lastModified}|$pixels"

    fun cached(item: FileItem, pixels: Int): Bitmap? = cache.get(keyOf(item, pixels))

    /**
     * Decodes one preview, or null where the file will not yield one.
     *
     * Blocking, and expected to be called off the main thread.
     */
    fun load(context: Context, item: FileItem, pixels: Int): Bitmap? {
        val key = keyOf(item, pixels)
        cache.get(key)?.let { return it }

        val bitmap = runCatching {
            when (item.kind) {
                FileKind.IMAGE -> decodeImage(item.path, pixels)
                FileKind.VIDEO -> decodeVideoFrame(item.path, pixels)
                FileKind.APK -> decodeApkIcon(context, item.path, pixels)
                else -> null
            }
        }.getOrNull()

        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /**
     * A picture, decoded straight to about the size it will be drawn at.
     *
     * Two passes: the first reads only the header for the dimensions, the
     * second decodes with a sample size chosen from them. Decoding at full size
     * and scaling afterwards is what makes a gallery of forty-megapixel
     * photographs run the heap out on the third row.
     */
    private fun decodeImage(path: String, pixels: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, pixels)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        return applyExifRotation(path, decoded)
    }

    /**
     * The largest power of two that still leaves the picture big enough.
     *
     * Powers of two because that is the only thing `inSampleSize` honours —
     * anything else is rounded down to one, silently.
     */
    private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Turns the picture the way the camera was held.
     *
     * Phones do not rotate the pixels; they write an orientation tag and leave
     * the data as the sensor read it. Ignoring it is why a thumbnail grid ends
     * up with every portrait photograph on its side.
     */
    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap

        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /** One frame from a video, taken wherever the file will give one up. */
    private fun decodeVideoFrame(path: String, pixels: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            // getFrameAtTime with no argument takes a representative frame,
            // which on most files is better than asking for time zero — the
            // first frame of a video is very often black.
            val frame = retriever.frameAtTime ?: return null
            scaleDown(frame, pixels)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * The launcher icon inside an APK.
     *
     * Worth the trouble because an APK's icon is the only thing that says which
     * app it is — the filename is as often `base.apk` or a version string as it
     * is the app's name.
     */
    private fun decodeApkIcon(context: Context, path: String, pixels: Int): Bitmap? {
        if (pixels <= 0 || !File(path).exists()) return null
        val packages = context.packageManager
        val info = packages.getPackageArchiveInfo(path, 0) ?: return null
        // An archive's ApplicationInfo has no paths filled in, so the icon
        // cannot be loaded until they are pointed back at the file itself.
        val application = info.applicationInfo ?: return null
        application.sourceDir = path
        application.publicSourceDir = path

        // loadIcon first, then loadUnbadgedIcon, then the bare icon resource.
        // They fail on different files rather than in a single order of
        // preference: loadIcon goes through the badging layer and throws on an
        // archive whose resources it cannot fully resolve, which is what left
        // some APKs with no icon at all while others were fine.
        val drawable: Drawable = runCatching { application.loadIcon(packages) }.getOrNull()
            ?: runCatching { application.loadUnbadgedIcon(packages) }.getOrNull()
            ?: runCatching {
                val resources = packages.getResourcesForApplication(application)
                val id = if (application.icon != 0) application.icon else info.applicationInfo!!.logo
                if (id == 0) null else androidx.core.content.res.ResourcesCompat
                    .getDrawable(resources, id, null)
            }.getOrNull()
            ?: return null

        // An adaptive icon reports no intrinsic size, so toBitmap has to be
        // told one or it throws. Asking for the size we are about to draw at is
        // the answer either way.
        return runCatching { drawable.toBitmap(pixels, pixels) }.getOrNull()
    }

    private fun scaleDown(bitmap: Bitmap, target: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= target) return bitmap
        val ratio = target.toFloat() / longest
        return runCatching {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        }.getOrDefault(bitmap)
    }
}

/**
 * The preview for one row, loaded off the main thread.
 *
 * Returns whatever is already cached on the very first composition, so a list
 * scrolled back over does not flash an icon before the picture returns. Keyed
 * on the file's identity, so recycling a row onto a different file cannot leave
 * the previous picture on screen.
 */
@Composable
fun rememberThumbnail(item: FileItem, location: Location, pixels: Int): Bitmap? {
    val context = LocalContext.current
    if (!Thumbnails.canPreview(item, location)) return null

    var bitmap by remember(item.path, item.size, item.lastModified) {
        mutableStateOf(Thumbnails.cached(item, pixels))
    }
    LaunchedEffect(item.path, item.size, item.lastModified) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { Thumbnails.load(context, item, pixels) }
        }
    }
    return bitmap
}
