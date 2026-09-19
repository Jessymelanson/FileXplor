package com.filexplor.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.Location
import com.filexplor.app.ui.ViewerState
import com.filexplor.app.ui.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A picture, full screen, with the rest of the folder either side of it.
 *
 * Swipe to move between them, pinch to zoom, double-tap to fill or fit. The
 * pager holds the whole folder's pictures rather than the one that was tapped,
 * because someone who opens the second of forty photographs is going to swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    viewer: ViewerState,
    location: Location,
    loadFull: suspend (FileItem) -> Bitmap?,
    onPage: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pager = rememberPagerState(initialPage = viewer.index) { viewer.items.size }
    var chromeVisible by remember { mutableStateOf(true) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect(onPage)
    }

    // A Box rather than a Scaffold, because the bar here overlays the picture
    // rather than sitting above it. Handing it to Scaffold as a topBar means
    // taking its padding and insetting the image by the height of the bar,
    // which on a viewer whose bar disappears on tap would make the picture
    // jump every time it was hidden.
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            // One page either side kept warm on the phone, so a swipe lands on
            // a picture rather than on a spinner -- and none of it on a server.
            //
            // The pager composes the pages either side of the one being looked
            // at, and each of them starts its own read the moment it is
            // composed. That is three reads at once down a connection that can
            // only carry one. FTP is the strict case and the one this was
            // reported on: a second RETR while the first is still streaming is
            // not a slow transfer, it is a protocol error on the control
            // channel, so all three pictures fail and the connection is left
            // confused for whatever is asked next.
            //
            // On the phone the reads are genuinely independent, and the warm
            // page either side is worth having.
            beyondViewportPageCount = if (location == Location.Device) 1 else 0,
            key = { page -> viewer.items[page].path }
        ) { page ->
            ZoomableImage(
                item = viewer.items[page],
                loadFull = loadFull,
                onTap = { chromeVisible = !chromeVisible },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Hidden on a tap, so a picture can actually be looked at. Kept as the
        // way back rather than relying on the system gesture alone, which is
        // invisible and not everyone's habit.
        if (chromeVisible) {
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        val item = viewer.items.getOrNull(pager.currentPage)
                        Column {
                            Text(
                                item?.name.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${pager.currentPage + 1} of ${viewer.items.size}" +
                                    (item?.let { " · ${formatBytes(it.size)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * One picture, pinchable.
 *
 * The gesture maths is deliberately simple: a scale and an offset, with the
 * offset cleared when the scale returns to one. Panning is only allowed while
 * zoomed in, which is what leaves the horizontal drag to the pager the rest of
 * the time — the two gestures are the same movement, and something has to
 * decide which one is meant.
 */
@Composable
private fun ZoomableImage(
    item: FileItem,
    loadFull: suspend (FileItem) -> Bitmap?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(item.path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.path) { mutableStateOf(false) }
    var scale by remember(item.path) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.path) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.path) { mutableFloatStateOf(0f) }

    LaunchedEffect(item.path) {
        val loaded = withContext(Dispatchers.IO) { runCatching { loadFull(item) }.getOrNull() }
        if (loaded == null) failed = true else bitmap = loaded
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            failed -> Text(
                "Couldn't open ${item.name}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            bitmap == null -> CircularProgressIndicator()

            else -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(item.path) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f; offsetX = 0f; offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(item.path) {
                        // Written out rather than using detectTransformGestures,
                        // which consumes every drag it sees. On a pager that is
                        // fatal: at rest the horizontal drag meant for swiping
                        // to the next picture was being eaten by the zoom
                        // handler, so swiping did nothing at all.
                        //
                        // Two fingers is always ours — nothing else here uses a
                        // pinch. One finger is ours only once zoomed in; at
                        // scale 1 it belongs to the pager and is left alone.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes.count { it.pressed }
                                val mine = pointers > 1 || scale > 1f
                                if (mine) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }
    }
}
