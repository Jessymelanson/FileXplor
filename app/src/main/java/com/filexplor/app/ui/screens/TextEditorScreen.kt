package com.filexplor.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filexplor.app.ui.EditorState
import com.filexplor.app.ui.formatBytes

/**
 * A small text editor: the file, and a Save.
 *
 * Monospaced, because most of what lands here is a config file, a log or
 * source, and a proportional font turns aligned columns into a mess. Markdown
 * is shown as its source rather than rendered — this is a file manager, and
 * what it should show is what is actually in the file.
 *
 * Deliberately not a code editor. No syntax colouring, no line numbers, no
 * find-and-replace. The job is to fix a typo in a config file on a server
 * without copying it to the phone and back, and everything past that is a
 * different app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    editor: EditorState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // On by default.
    //
    // This started off, on the reasoning that a log or a config file has
    // meaningful line ends and wrapping hides where one record stops. That is
    // true on a wide screen and wrong on a phone: a 90-column line is most of a
    // metre of horizontal scrolling, so the file opened showing the left third
    // of itself and nothing could be read without dragging sideways line by
    // line. Wrapping makes the common case — notes, Markdown, a README —
    // readable the moment it opens, and the toggle is still there for the log.
    var wrap by remember { mutableStateOf(true) }

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
                        Text(
                            editor.item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                // The dot is the convention for unsaved work and
                                // costs a character; saying it in words as well
                                // is what makes it unmissable on a small bar.
                                if (editor.dirty) append("Unsaved changes · ")
                                if (editor.readOnly) append("Read-only · ")
                                append(formatBytes(editor.text.length.toLong()))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (editor.dirty) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { wrap = !wrap }) {
                        Icon(
                            Icons.AutoMirrored.Filled.WrapText,
                            contentDescription = if (wrap) "Stop wrapping lines" else "Wrap lines",
                            tint = if (wrap) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (editor.saving) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    } else {
                        // Enabled only when there is something to save, so the
                        // button's state is the answer to "did that go through".
                        IconButton(onClick = onSave, enabled = editor.dirty) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = "Save",
                                tint = if (editor.dirty) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        // BasicTextField rather than the Material one: at this size the filled
        // decoration, its label and its indicator line take a fifth of the
        // screen to say nothing the top bar has not already said.
        BasicTextField(
            value = editor.text,
            onValueChange = onTextChange,
            readOnly = editor.readOnly,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                // Wrapping is decided by the width the field is given, not by a
                // parameter — the String form of BasicTextField has no
                // `softWrap`. Bounded width and the text wraps; add a
                // horizontal scroll and the width becomes unbounded, so it lays
                // out on one long line instead. That is the whole mechanism,
                // and it is worth naming because nothing in the call says so.
                .then(
                    if (wrap) Modifier.verticalScroll(rememberScrollState())
                    else Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScrollIfNeeded()
                )
        )
    }
}

/**
 * Sideways scrolling for unwrapped text.
 *
 * Split out only so the modifier chain above stays readable; a long line has to
 * go somewhere, and off the right-hand edge with a scrollbar is better than
 * folded into a shape the file does not have.
 */
@Composable
private fun Modifier.horizontalScrollIfNeeded(): Modifier =
    this.horizontalScroll(rememberScrollState())
