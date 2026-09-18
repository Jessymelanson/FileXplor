package com.filexplor.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filexplor.app.data.Progress

/**
 * What a running copy, move, delete or download looks like.
 *
 * Lives here rather than inside the browse screen, which is where it used to
 * be and is the whole of why it kept disappearing. A copy takes minutes, and in
 * those minutes people open the file they were looking at, go back to the home
 * screen to find the other folder, or read a text file — and every one of those
 * left the browse screen, taking the only sign that anything was happening with
 * it. The activity hosts this now, below whatever screen is in front, so the
 * job is visible and stoppable from anywhere in the app.
 *
 * The indeterminate bar is not a detail either. A paste walks the whole tree
 * before it moves a byte, which on a server is a listing per folder and can run
 * for several seconds, and the bar used to appear only once the first file
 * started — so the reply to tapping Paste was nothing at all, for long enough
 * to tap it again.
 */
@Composable
fun JobBar(
    title: String,
    progress: Progress,
    onCancel: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) { Text("Stop") }
            }
            if (progress.preparing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            Text(
                progress.currentName.ifBlank { "Working out what to move…" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
