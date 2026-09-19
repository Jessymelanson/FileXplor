@file:OptIn(ExperimentalLayoutApi::class)

package com.filexplor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.filexplor.app.data.ThemeMode
import com.filexplor.app.data.ThemePalette
import com.filexplor.app.ui.theme.paletteSwatch

/**
 * Appearance lives in its own dialog rather than a toolbar dropdown. Three
 * modes and thirty-two palettes is thirty-five menu rows — a scrolling list of
 * colour names nobody can picture. Here the modes are chips and the palettes
 * are their own swatches, so the choice is visible rather than described.
 *
 * Twenty-four of the palettes carry an animated backdrop, which is the other
 * reason for swatches over names: "Sonar", "Camo" and "Contours" say nothing at
 * all until one has been seen.
 */
@Composable
fun AppearanceDialog(
    themeMode: ThemeMode,
    themePalette: ThemePalette,
    isDarkTheme: Boolean,
    onModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (ThemePalette) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("Mode")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == themeMode,
                            onClick = { onModeChange(mode) },
                            label = {
                                // "System default" is too long for a chip that
                                // sits beside two one-word siblings.
                                Text(if (mode == ThemeMode.SYSTEM) "System" else mode.label)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("Theme")
                Spacer(Modifier.height(4.dp))

                ThemePalette.entries.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { palette ->
                            PaletteSwatch(
                                palette = palette,
                                selected = palette == themePalette,
                                isDarkTheme = isDarkTheme,
                                onClick = { onPaletteChange(palette) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keeps a short final row aligned with the one above it.
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(12.dp))
                }

            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatch = paletteSwatch(palette, isDarkTheme)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    // Against a light swatch a white tick disappears.
                    tint = if (swatch.luminance() > 0.5f) Color(0xFF101010) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            palette.label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
