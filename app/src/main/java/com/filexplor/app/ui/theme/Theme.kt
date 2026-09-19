package com.filexplor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.filexplor.app.data.ThemePalette

/**
 * Each palette is described by six colours — an accent, a ground, and an ink,
 * for light and for dark — and the full Material scheme is derived from those.
 *
 * Deriving rather than hand-listing matters here: Material 3 draws bottom sheets
 * from surfaceContainerLow and dialogs from surfaceContainerHigh, so a scheme
 * that defines only `surface` falls back to Material's purple-tinted defaults
 * for everything else and the app ends up two-toned. Generating the whole
 * container ramp from the ground colour means a new palette can't reintroduce
 * that bug by omission — including primaryContainer and secondaryContainer,
 * which a floating action button and a selected navigation item use, and which
 * were the two that slipped through and rendered lavender in every palette.
 */
private data class PaletteSpec(
    val lightAccent: Color,
    val lightGround: Color,
    val lightInk: Color,
    val darkAccent: Color,
    val darkGround: Color,
    val darkInk: Color
)

private val PALETTES: Map<ThemePalette, PaletteSpec> = mapOf(
    // Eight, and every one of them changes the ground.
    //
    // There were sixteen, and they were sixteen versions of the same theme: the
    // same near-white paper and the same near-black night, with a different
    // button colour on top. Two of them side by side were hard to tell apart
    // and picking between them was picking an accent, which is not what a theme
    // is. What actually reads as a different app is the colour of the page —
    // so each palette here owns its ground and its ink, and the accent is the
    // last thing that changes rather than the only one.

    // Warm off-white and a soft black. The default, and the quietest.
    ThemePalette.PAPER to PaletteSpec(
        lightAccent = Color(0xFF3D6B5C), lightGround = Color(0xFFFBFAF7), lightInk = Color(0xFF1B1B1F),
        darkAccent = Color(0xFF8FCBB6), darkGround = Color(0xFF121212), darkInk = Color(0xFFE6E1E5)
    ),
    // Blue all the way down: a page the colour of shallow water, a night the
    // colour of deep water. Not white with a blue button.
    ThemePalette.OCEAN to PaletteSpec(
        lightAccent = Color(0xFF0B6E8C), lightGround = Color(0xFFE7F0F6), lightInk = Color(0xFF0D2029),
        darkAccent = Color(0xFF57C8E5), darkGround = Color(0xFF061620), darkInk = Color(0xFFCDE5F0)
    ),
    ThemePalette.FOREST to PaletteSpec(
        lightAccent = Color(0xFF2E6B3E), lightGround = Color(0xFFEAF2E6), lightInk = Color(0xFF16210F),
        darkAccent = Color(0xFF86D08F), darkGround = Color(0xFF0D1A10), darkInk = Color(0xFFDAE8D4)
    ),
    // The warm one. Peach in the light, and a brown-black rather than a grey
    // one in the dark, so the warmth survives turning the lights off.
    ThemePalette.SUNSET to PaletteSpec(
        lightAccent = Color(0xFFC2571F), lightGround = Color(0xFFFFEFE3), lightInk = Color(0xFF2E170D),
        darkAccent = Color(0xFFFF9E5E), darkGround = Color(0xFF1F120A), darkInk = Color(0xFFF7DFCC)
    ),
    // One purple instead of the three that used to be here. Lavender, Rose and
    // Plum were a soft one, a pink one and a dark one, and at a glance on a
    // phone they were the same theme three times.
    ThemePalette.VIOLET to PaletteSpec(
        lightAccent = Color(0xFF6A4FA3), lightGround = Color(0xFFF1EBFB), lightInk = Color(0xFF1D1530),
        darkAccent = Color(0xFFBFA6F5), darkGround = Color(0xFF150F20), darkInk = Color(0xFFE6DDF7)
    ),
    // Nord's own values. Its light form is a cool grey rather than a white, and
    // its dark form is far lighter than any other night here — that contrast
    // with everything else is the reason to keep it.
    ThemePalette.NORD to PaletteSpec(
        lightAccent = Color(0xFF5E81AC), lightGround = Color(0xFFE5E9F0), lightInk = Color(0xFF2E3440),
        darkAccent = Color(0xFF88C0D0), darkGround = Color(0xFF2E3440), darkInk = Color(0xFFECEFF4)
    ),
    // Ethan Schoonover's base values, not an approximation. Cream and deep
    // teal, which no other palette here goes near.
    ThemePalette.SOLARIZED to PaletteSpec(
        lightAccent = Color(0xFF268BD2), lightGround = Color(0xFFFDF6E3), lightInk = Color(0xFF073642),
        darkAccent = Color(0xFF2AA198), darkGround = Color(0xFF002B36), darkInk = Color(0xFFEEE8D5)
    ),
    // Paper's opposite rather than its neighbour: pure white and pure black,
    // no warmth and no tint, and a true black that an OLED screen switches off
    // rather than lights dimly.
    ThemePalette.MONO to PaletteSpec(
        lightAccent = Color(0xFF2B2B2B), lightGround = Color(0xFFFFFFFF), lightInk = Color(0xFF000000),
        darkAccent = Color(0xFFC8C8C8), darkGround = Color(0xFF000000), darkInk = Color(0xFFFFFFFF)
    ),
    // The animated sixteen. Each still earns its place as a still palette — the
    // ground and ink are chosen to read well with the motion switched off, so
    // that somebody who picks one for the colour is not stuck with a colour
    // that only worked because something was moving over it.

    // Navy, so a gold shell going off has somewhere dark to go off in.
    ThemePalette.SPARKS to PaletteSpec(
        lightAccent = Color(0xFF2A3E7A), lightGround = Color(0xFFEDF1F8), lightInk = Color(0xFF10182E),
        darkAccent = Color(0xFFFFD54F), darkGround = Color(0xFF060A18), darkInk = Color(0xFFDDE4F2)
    ),
    // Wet slate. The one palette here that is neither warm nor a night sky.
    ThemePalette.STORM to PaletteSpec(
        lightAccent = Color(0xFF2E6B75), lightGround = Color(0xFFE9EFF0), lightInk = Color(0xFF14252A),
        darkAccent = Color(0xFF7FC4C9), darkGround = Color(0xFF0C1417), darkInk = Color(0xFFD3E3E5)
    ),
    // Hot magenta in deep purple glass.
    ThemePalette.LAVA to PaletteSpec(
        lightAccent = Color(0xFFA02064), lightGround = Color(0xFFF7EAF6), lightInk = Color(0xFF2A0F2E),
        darkAccent = Color(0xFFFF5FA2), darkGround = Color(0xFF1B0A24), darkInk = Color(0xFFF3DCEC)
    ),
    // Aquarium green-blue, the one colour the family did not already own.
    ThemePalette.REEF to PaletteSpec(
        lightAccent = Color(0xFF127D74), lightGround = Color(0xFFE4F4F3), lightInk = Color(0xFF0B2A2C),
        darkAccent = Color(0xFF4DD0C7), darkGround = Color(0xFF04181C), darkInk = Color(0xFFD2ECE9)
    ),
    // Nearly black, with warm stars rather than white ones.
    ThemePalette.COSMOS to PaletteSpec(
        lightAccent = Color(0xFF4A4E8C), lightGround = Color(0xFFF0F0F5), lightInk = Color(0xFF15162A),
        darkAccent = Color(0xFFF2D68A), darkGround = Color(0xFF05060F), darkInk = Color(0xFFDFE0EC)
    ),
    // Survey paper and pencil. The quietest of the animated set.
    ThemePalette.RELIEF to PaletteSpec(
        lightAccent = Color(0xFF8A5A2B), lightGround = Color(0xFFF7F1E6), lightInk = Color(0xFF2B2419),
        darkAccent = Color(0xFFD9A86B), darkGround = Color(0xFF14100B), darkInk = Color(0xFFEADFCB)
    ),
    // Green phosphor on a dead-black CRT.
    ThemePalette.PHOSPHOR to PaletteSpec(
        lightAccent = Color(0xFF117A33), lightGround = Color(0xFFF0F5F0), lightInk = Color(0xFF0B1A0E),
        darkAccent = Color(0xFF3BE86B), darkGround = Color(0xFF010A03), darkInk = Color(0xFFCFE8D6)
    ),
    // Charcoal-indigo and amber: an instrument, not a landscape.
    ThemePalette.ORRERY to PaletteSpec(
        lightAccent = Color(0xFFB07714), lightGround = Color(0xFFF2F1EC), lightInk = Color(0xFF1C1D28),
        darkAccent = Color(0xFFF0B457), darkGround = Color(0xFF0B0D16), darkInk = Color(0xFFE2E3EE)
    ),
    // Paler and warmer than Pink hearts, which is the loud pink of the two.
    ThemePalette.BLOSSOMFALL to PaletteSpec(
        lightAccent = Color(0xFFE08BA8), lightGround = Color(0xFFFDF3F1), lightInk = Color(0xFF33202A),
        darkAccent = Color(0xFFF7C2D2), darkGround = Color(0xFF1C1216), darkInk = Color(0xFFF4E3E7)
    ),
    // The loudest thing in the picker, and unapologetically so.
    ThemePalette.RETRO to PaletteSpec(
        lightAccent = Color(0xFFB01271), lightGround = Color(0xFFF6EBFA), lightInk = Color(0xFF250B31),
        darkAccent = Color(0xFFFF2E97), darkGround = Color(0xFF14061F), darkInk = Color(0xFFF2DCF0)
    ),
    // Pale rose and plum, so pink hearts land on something already pink.
    ThemePalette.BLOSSOM to PaletteSpec(
        lightAccent = Color(0xFFC2185B), lightGround = Color(0xFFFDEAF2), lightInk = Color(0xFF3A1020),
        darkAccent = Color(0xFFF48FB1), darkGround = Color(0xFF1A0A12), darkInk = Color(0xFFF7DCE6)
    ),
    // Ice: a white page with blue in it, and a night that is not quite black.
    ThemePalette.FROST to PaletteSpec(
        lightAccent = Color(0xFF3E7CA6), lightGround = Color(0xFFF2F7FB), lightInk = Color(0xFF16242E),
        darkAccent = Color(0xFF9FD3F0), darkGround = Color(0xFF0A1119), darkInk = Color(0xFFDCE9F2)
    ),
    // The only warm one of the five. Rust and cream, against a brown-black
    // rather than a grey one, so the leaves have somewhere to land.
    ThemePalette.AUTUMN to PaletteSpec(
        lightAccent = Color(0xFFB4531B), lightGround = Color(0xFFFBF0E2), lightInk = Color(0xFF2E1B0C),
        darkAccent = Color(0xFFE8A15C), darkGround = Color(0xFF1A1008), darkInk = Color(0xFFF0DFC9)
    ),
    // Deep indigo for the curtains to hang against.
    ThemePalette.BOREALIS to PaletteSpec(
        lightAccent = Color(0xFF3F51B5), lightGround = Color(0xFFEDEEFA), lightInk = Color(0xFF151A33),
        darkAccent = Color(0xFF64FFDA), darkGround = Color(0xFF070B18), darkInk = Color(0xFFD5DCF0)
    ),
    // Pumpkin against a bruised purple dusk, rather than the flat black the
    // season usually gets — bats need a sky to be silhouetted against.
    ThemePalette.HALLOWEEN to PaletteSpec(
        lightAccent = Color(0xFFC75B10), lightGround = Color(0xFFF6EDE2), lightInk = Color(0xFF2B1B33),
        darkAccent = Color(0xFFFF9D3D), darkGround = Color(0xFF241137), darkInk = Color(0xFFF2E2D2)
    ),
    // Deep pine, so the coloured bulbs have something to hang in front of.
    ThemePalette.YULE to PaletteSpec(
        lightAccent = Color(0xFFB3261E), lightGround = Color(0xFFF2F7F0), lightInk = Color(0xFF14291B),
        darkAccent = Color(0xFFE8C05A), darkGround = Color(0xFF08150E), darkInk = Color(0xFFE2EDE2)
    ),
    // Eight more, and the brief for all of them was the same: workshop, weather
    // and machinery rather than confetti. They lean dark, they lean grey, and
    // the warm ones get their warmth from heat rather than from sugar.

    // Redline. Near-black iron and the one true red in the whole set — no
    // other palette here uses it, which is what keeps an engine from reading
    // as a warmer version of the brass one.
    ThemePalette.PISTON to PaletteSpec(
        lightAccent = Color(0xFFB02B1C), lightGround = Color(0xFFF4F1EF), lightInk = Color(0xFF1E1A19),
        darkAccent = Color(0xFFE8412B), darkGround = Color(0xFF100F10), darkInk = Color(0xFFE6E1DF)
    ),
    // Slate and a lightning white with blue in it, over a sky that is almost
    // but not quite black — a storm at night still has cloud in it.
    ThemePalette.TEMPEST to PaletteSpec(
        lightAccent = Color(0xFF3A5A78), lightGround = Color(0xFFEDF1F5), lightInk = Color(0xFF161C24),
        darkAccent = Color(0xFFBFD8F5), darkGround = Color(0xFF0D1117), darkInk = Color(0xFFDCE3EC)
    ),
    // Brass against gunmetal. Machinery is two metals, and the warm one is
    // always the smaller part of it.
    ThemePalette.MACHINE to PaletteSpec(
        lightAccent = Color(0xFF8A6A22), lightGround = Color(0xFFF3F1EC), lightInk = Color(0xFF22201B),
        darkAccent = Color(0xFFD9A441), darkGround = Color(0xFF14161A), darkInk = Color(0xFFE3E1DA)
    ),
    // Phosphor amber on instrument black. Amber rather than the other classic
    // scope colour, because green is already spoken for twice over by the
    // terminal, and a second green screen is a duplicate rather than a theme.
    ThemePalette.SCOPE to PaletteSpec(
        lightAccent = Color(0xFF8A5D00), lightGround = Color(0xFFF6F3EC), lightInk = Color(0xFF201B12),
        darkAccent = Color(0xFFFFB020), darkGround = Color(0xFF0A0C0A), darkInk = Color(0xFFE6E3DA)
    ),
    // Scope cyan rather than the usual green, so it never reads as a second
    // Terminal, over the blue-black of a screen below the waterline.
    ThemePalette.DEEP to PaletteSpec(
        lightAccent = Color(0xFF0E6B7E), lightGround = Color(0xFFEAF1F4), lightInk = Color(0xFF0B1F26),
        darkAccent = Color(0xFF35C9E0), darkGround = Color(0xFF04121B), darkInk = Color(0xFFCCE4EA)
    ),
    // Actual blueprint blue, which is darker than people remember, and the
    // chalky white that draughtsmen drew on it with.
    ThemePalette.DRAFT to PaletteSpec(
        lightAccent = Color(0xFF1E5FA8), lightGround = Color(0xFFEDF2F8), lightInk = Color(0xFF102438),
        darkAccent = Color(0xFF9EC6EE), darkGround = Color(0xFF0A2540), darkInk = Color(0xFFD7E4F2)
    ),
    // Copper on board green. The two colours every circuit board has ever had,
    // and the only pairing here that is a material rather than a mood.
    ThemePalette.BOARD to PaletteSpec(
        lightAccent = Color(0xFF9A6B1E), lightGround = Color(0xFFEFF3EE), lightInk = Color(0xFF13231B),
        darkAccent = Color(0xFFD8A24A), darkGround = Color(0xFF0A1410), darkInk = Color(0xFFDCE6DE)
    ),
    // Olive drab, and a ground that is the darkest green rather than a grey,
    // so the pattern has something to be a pattern against.
    ThemePalette.RECON to PaletteSpec(
        lightAccent = Color(0xFF4E6238), lightGround = Color(0xFFEFF0E6), lightInk = Color(0xFF1F2416),
        darkAccent = Color(0xFF9DB076), darkGround = Color(0xFF141810), darkInk = Color(0xFFDDE2D0)
    )
)

private val ErrorLight = Color(0xFFB3261E)
private val ErrorDark = Color(0xFFF2B8B5)

private fun Color.mix(other: Color, ratio: Float): Color = Color(
    red = red + (other.red - red) * ratio,
    green = green + (other.green - green) * ratio,
    blue = blue + (other.blue - blue) * ratio,
    alpha = 1f
)

/** Black or white, whichever actually reads against the given colour. */
private fun contrastOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF101010) else Color(0xFFFFFFFF)

private fun buildLight(spec: PaletteSpec): ColorScheme {
    val accent = spec.lightAccent
    val ground = spec.lightGround
    val ink = spec.lightInk
    return lightColorScheme(
        primary = spec.lightAccent,
        onPrimary = contrastOn(spec.lightAccent),
        secondary = spec.lightAccent.mix(ink, 0.2f),
        onSecondary = contrastOn(spec.lightAccent.mix(ink, 0.2f)),
        background = ground,
        onBackground = ink,
        surface = ground,
        onSurface = ink,
        surfaceVariant = ground.mix(ink, 0.07f),
        onSurfaceVariant = ink.mix(ground, 0.35f),
        // Lighter than the ground but the same colour as it. Pure white here
        // was fine while every ground was near-white anyway; now that Ocean is
        // blue and Solarized is cream, a white card on top of them reads as a
        // hole in the page rather than a surface above it.
        surfaceContainerLowest = ground.mix(Color.White, 0.65f),
        surfaceContainerLow = ground,
        surfaceContainer = ground.mix(ink, 0.04f),
        surfaceContainerHigh = ground.mix(ink, 0.07f),
        surfaceContainerHighest = ground.mix(ink, 0.10f),
        outline = ink.mix(ground, 0.55f),
        outlineVariant = ink.mix(ground, 0.80f),
        // The container roles, derived like everything else.
        //
        // Left undefined, these fall back to Material's baseline lavender —
        // and they are exactly the roles the most prominent controls use: a
        // floating action button is primaryContainer, a selected navigation
        // item and a chosen FilterChip are secondaryContainer. The result was
        // that the brightest thing on the screen belonged to no palette in the
        // app, in whichever palette the user had picked.
        primaryContainer = accent.mix(ground, 0.78f),
        onPrimaryContainer = accent.mix(Color.Black, 0.45f),
        secondaryContainer = accent.mix(ground, 0.86f),
        onSecondaryContainer = accent.mix(Color.Black, 0.50f),
        tertiary = accent.mix(ink, 0.20f),
        onTertiary = contrastOn(accent.mix(ink, 0.20f)),
        tertiaryContainer = accent.mix(ground, 0.86f),
        onTertiaryContainer = accent.mix(Color.Black, 0.50f),
        errorContainer = ErrorLight.mix(ground, 0.85f),
        onErrorContainer = ErrorLight.mix(Color.Black, 0.45f),
        inverseSurface = ink,
        inverseOnSurface = ground,
        inversePrimary = accent.mix(ground, 0.45f),
        surfaceTint = accent,
        scrim = Color.Black,
        error = ErrorLight,
        onError = Color.White
    )
}

private fun buildDark(spec: PaletteSpec): ColorScheme {
    val accent = spec.darkAccent
    val ground = spec.darkGround
    val ink = spec.darkInk
    return darkColorScheme(
        primary = spec.darkAccent,
        onPrimary = contrastOn(spec.darkAccent),
        secondary = spec.darkAccent.mix(ground, 0.25f),
        onSecondary = contrastOn(spec.darkAccent.mix(ground, 0.25f)),
        background = ground,
        onBackground = ink,
        surface = ground.mix(ink, 0.05f),
        onSurface = ink,
        surfaceVariant = ground.mix(ink, 0.12f),
        onSurfaceVariant = ink.mix(ground, 0.25f),
        surfaceContainerLowest = ground.mix(Color.Black, 0.4f),
        surfaceContainerLow = ground.mix(ink, 0.03f),
        surfaceContainer = ground.mix(ink, 0.06f),
        surfaceContainerHigh = ground.mix(ink, 0.10f),
        surfaceContainerHighest = ground.mix(ink, 0.14f),
        outline = ink.mix(ground, 0.55f),
        outlineVariant = ink.mix(ground, 0.78f),
        // Same roles, inverted: in the dark the container is the dark tint and
        // the accent itself is what reads on top of it.
        primaryContainer = accent.mix(ground, 0.68f),
        onPrimaryContainer = accent.mix(Color.White, 0.20f),
        secondaryContainer = accent.mix(ground, 0.78f),
        onSecondaryContainer = accent.mix(Color.White, 0.25f),
        tertiary = accent.mix(ground, 0.20f),
        onTertiary = contrastOn(accent.mix(ground, 0.20f)),
        tertiaryContainer = accent.mix(ground, 0.78f),
        onTertiaryContainer = accent.mix(Color.White, 0.25f),
        errorContainer = ErrorDark.mix(ground, 0.72f),
        onErrorContainer = ErrorDark.mix(Color.White, 0.20f),
        inverseSurface = ink,
        inverseOnSurface = ground,
        inversePrimary = accent.mix(ground, 0.55f),
        surfaceTint = accent,
        scrim = Color.Black,
        error = ErrorDark,
        onError = Color(0xFF601410)
    )
}

private val FileXplorTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

/** Accent swatch for the palette picker, so the menu shows the colour itself
 *  rather than asking the user to guess what "Nord" looks like. */
fun paletteSwatch(palette: ThemePalette, darkTheme: Boolean): Color {
    val spec = PALETTES[palette] ?: PALETTES.getValue(ThemePalette.PAPER)
    return if (darkTheme) spec.darkAccent else spec.lightAccent
}

@Composable
fun FileXplorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.PAPER,
    content: @Composable () -> Unit
) {
    val spec = PALETTES[palette] ?: PALETTES.getValue(ThemePalette.PAPER)
    val base = if (darkTheme) buildDark(spec) else buildLight(spec)
    val motion = palette.motion

    // An animated palette hands the page over to the backdrop.
    //
    // background and surface go transparent so the canvas behind them is what
    // shows through, which is what makes this a single change here rather than
    // an edit to every screen: Scaffold paints `background` and the app's root
    // Surface paints `surface`, and both simply stop painting.
    //
    // Only those two. Cards, dialogs, text fields, app bars and navigation bars
    // all draw from the surfaceContainer ramp, which stays opaque — so the
    // motion is confined to the empty page and never runs under a paragraph.
    val colors = if (motion == null) {
        base
    } else {
        base.copy(background = Color.Transparent, surface = Color.Transparent)
    }
    MaterialTheme(colorScheme = colors, typography = FileXplorTypography) {
        if (motion == null) {
            content()
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                MotionBackdrop(
                    motion = motion,
                    accent = if (darkTheme) spec.darkAccent else spec.lightAccent,
                    ground = if (darkTheme) spec.darkGround else spec.lightGround,
                    darkTheme = darkTheme
                )
                content()
            }
        }
    }
}

/**
 * True while an animated palette is drawing behind the page.
 *
 * An animated palette publishes a transparent `background` so the backdrop
 * shows through, and that is the signal — there is no second flag to keep in
 * step with the palette list.
 *
 * A screen that paints a large opaque area of its own needs to check this and
 * go translucent, or it hides the very thing it is sitting in front of.
 * JCalendar's month grid is the case that forced this: thirty-five filled cells
 * covering almost the whole page left the animation visible only in the
 * hairline gaps between them.
 */
@Composable
fun backdropRunning(): Boolean = MaterialTheme.colorScheme.background.alpha == 0f
