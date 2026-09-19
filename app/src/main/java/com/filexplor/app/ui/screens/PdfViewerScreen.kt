package com.filexplor.app.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filexplor.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A PDF, one page under another.
 *
 * `PdfRenderer` has been in Android since Lollipop and needs no dependency, so
 * a file manager showing a PDF costs a screen rather than a library. It is a
 * viewer and nothing else: no text selection, no search, no links. "Open with"
 * stays in the bar for the reader that does all three.
 *
 * Pages are rendered as they scroll into view and not before. A prospectus of
 * two hundred pages rendered up front is tens of seconds and a heap full of
 * bitmaps nobody has looked at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    item: FileItem,
    onOpenExternally: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pages by remember(item.path) { mutableStateOf(0) }
    var failure by remember(item.path) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.path) {
        val count = withContext(Dispatchers.IO) {
            runCatching { openRenderer(item.path).use { it.pageCount } }
        }
        count.onSuccess { pages = it }
            .onFailure { failure = it.message ?: "This PDF could not be opened." }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
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
                    Column {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (pages > 0) {
                            Text(
                                "$pages ${if (pages == 1) "page" else "pages"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenExternally) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open with another app")
                    }
                }
            )
        }
    ) { padding ->
        when {
            failure != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(failure!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            pages == 0 -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(pages, key = { it }) { index -> PdfPage(item.path, index) }
            }
        }
    }
}

@Composable
private fun PdfPage(path: String, index: Int) {
    var bitmap by remember(path, index) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path, index) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { renderPage(path, index) }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val page = bitmap
        if (page == null) {
            // A placeholder the height of a page, so the list does not jump as
            // each one finishes rendering.
            Box(Modifier.fillMaxWidth().padding(vertical = 180.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun openRenderer(path: String): PdfRenderer {
    val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    return PdfRenderer(descriptor)
}

/**
 * One page, rendered at a fixed width.
 *
 * A renderer is opened and closed per page rather than held. `PdfRenderer`
 * allows exactly one page open at a time and is not thread-safe, so keeping one
 * around means every page render has to queue behind the others — which is the
 * same cost, plus the bug where two of them race.
 */
private fun renderPage(path: String, index: Int, width: Int = 1240): Bitmap? =
    openRenderer(path).use { renderer ->
        if (index !in 0 until renderer.pageCount) return null
        renderer.openPage(index).use { page ->
            val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // White first: a PDF page is transparent where nothing is drawn, and
            // rendered onto an empty bitmap the text arrives on a black ground.
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }
