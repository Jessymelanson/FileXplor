package com.filexplor.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.filexplor.app.data.ThemeMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The moving part of an animated theme.
 *
 * Drawn once, behind everything, by the app's theme wrapper — not per screen.
 * The palettes that use it publish a transparent `background` and `surface`, so
 * the page itself is this canvas and the app's own text sits straight on top of
 * it. Cards, dialogs, app bars and navigation bars all draw from the
 * surfaceContainer ramp instead, which stays opaque, so the chrome never has
 * anything moving underneath the words.
 *
 * Everything here is deliberately cheap and deliberately quiet. A note app with
 * a fire behind the text is unreadable, so the shapes are few, slow, soft-edged
 * and low-alpha: the effect should register at a glance and then get out of the
 * way of whatever the user is actually reading.
 */

/** One drifting shape. Fixed at first composition, never mutated after. */
private data class Mote(
    /** Across the screen, 0..1. */
    val x: Float,
    /** Fraction of the shorter screen edge. */
    val size: Float,
    /** Multiplies the shared clock, so they do not travel as a block. */
    val speed: Float,
    /** Start position along the journey, 0..1, so they do not start in a row. */
    val phase: Float,
    /** How far it wanders sideways, in fractions of the width. */
    val sway: Float,
    /** Per-mote opacity, so the field has depth rather than one flat layer. */
    val alpha: Float
)

/**
 * Seeded on the motion, so a theme looks the same every time it is opened
 * rather than reshuffling on every rotation or recomposition.
 */
private fun motes(motion: ThemeMotion, count: Int): List<Mote> {
    val random = Random(motion.ordinal * 977 + count)
    return List(count) {
        Mote(
            x = random.nextFloat(),
            size = 0.02f + random.nextFloat() * 0.04f,
            speed = 0.6f + random.nextFloat() * 0.8f,
            phase = random.nextFloat(),
            sway = 0.02f + random.nextFloat() * 0.06f,
            alpha = 0.25f + random.nextFloat() * 0.45f
        )
    }
}

/** How many shapes each motion wants. The set pieces do not use motes at all. */
private fun countFor(motion: ThemeMotion): Int = when (motion) {
    ThemeMotion.LIGHTS, ThemeMotion.SYNTHWAVE, ThemeMotion.CONTOURS,
    ThemeMotion.TERMINAL, ThemeMotion.ORBITS, ThemeMotion.THUNDER,
    ThemeMotion.GEARS, ThemeMotion.OSCILLOSCOPE, ThemeMotion.SONAR,
    ThemeMotion.BLUEPRINT, ThemeMotion.CIRCUIT, ThemeMotion.ENGINE -> 0
    ThemeMotion.CAMO -> 18
    ThemeMotion.AURORA -> 4
    ThemeMotion.LAVA -> 6
    ThemeMotion.FIREWORKS -> 6
    ThemeMotion.CONSTELLATIONS -> 14
    ThemeMotion.BUBBLES -> 22
    ThemeMotion.BATS -> 7
    ThemeMotion.LEAVES -> 16
    ThemeMotion.PETALS -> 22
    ThemeMotion.RAIN -> 60
    else -> 26
}

@Composable
fun MotionBackdrop(
    motion: ThemeMotion,
    accent: Color,
    ground: Color,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    // One clock for the whole field. Each shape derives its own position from
    // it, so there is a single animation running rather than one per shape.
    val clock = rememberInfiniteTransition(label = "backdrop")
    val time = clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing)
        ),
        label = "time"
    )

    val field = remember(motion) { motes(motion, countFor(motion)) }

    // Measured once and reused every frame. Laying out text inside the draw
    // would be the only expensive thing in this file.
    val measurer = rememberTextMeasurer()
    val glyphs = remember(measurer, motion) {
        if (motion != ThemeMotion.TERMINAL) emptyList() else GLYPHS.map { ch ->
            measurer.measure(
                AnnotatedString(ch.toString()),
                TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read inside the draw block on purpose: the value changes every frame,
        // and reading it here re-runs only the drawing rather than recomposing
        // the whole tree behind it.
        val t = time.value

        drawRect(color = ground)

        when (motion) {
            ThemeMotion.FIREWORKS -> drawFireworks(field, t, accent)
            ThemeMotion.RAIN -> drawRain(field, t, accent, darkTheme)
            ThemeMotion.LAVA -> drawLava(field, t, accent)
            ThemeMotion.BUBBLES -> drawBubbles(field, t, accent)
            ThemeMotion.CONSTELLATIONS -> drawConstellations(field, t, accent)
            ThemeMotion.SYNTHWAVE -> drawSynthwave(t, accent, darkTheme, ground)
            ThemeMotion.CONTOURS -> drawContours(t, accent, darkTheme)
            ThemeMotion.TERMINAL -> drawTerminal(t, accent, darkTheme, glyphs)
            ThemeMotion.ORBITS -> drawOrbits(t, accent)
            ThemeMotion.PETALS -> drawPetals(field, t, accent)
            ThemeMotion.HEARTS -> drawHearts(field, t, accent)
            ThemeMotion.SNOW -> drawSnow(field, t, accent, darkTheme)
            ThemeMotion.LEAVES -> drawLeaves(field, t, accent)
            ThemeMotion.AURORA -> drawAurora(field, t, accent)
            ThemeMotion.BATS -> drawBats(field, t, accent)
            ThemeMotion.LIGHTS -> drawFairyLights(t, accent, darkTheme)
            ThemeMotion.ENGINE -> drawEngine(t, accent, darkTheme)
            ThemeMotion.THUNDER -> drawThunder(t, accent, darkTheme)
            ThemeMotion.GEARS -> drawGears(t, accent, darkTheme)
            ThemeMotion.OSCILLOSCOPE -> drawOscilloscope(t, accent, darkTheme)
            ThemeMotion.SONAR -> drawSonar(t, accent, darkTheme)
            ThemeMotion.BLUEPRINT -> drawBlueprint(t, accent, darkTheme)
            ThemeMotion.CIRCUIT -> drawCircuit(t, accent, darkTheme)
            ThemeMotion.CAMO -> drawCamo(field, t, accent)
        }
    }
}

// ------------------------------------------------------------ fireworks ----

/**
 * Shells bursting and falling.
 *
 * The only motion in the set that expands — everything else travels in a line
 * or hangs still — which is what keeps it from reading as another drifting
 * field. Each shell runs its own loop: a hard burst, an eased spread outward,
 * then gravity pulling the sparks down as they go out.
 */
private fun DrawScope.drawFireworks(field: List<Mote>, t: Float, accent: Color) {
    val colours = listOf(
        accent,
        lerp(accent, Color(0xFFFF5E5E), 0.7f),
        lerp(accent, Color(0xFF5EE7FF), 0.7f),
        lerp(accent, Color(0xFFB98CFF), 0.65f),
        lerp(accent, Color.White, 0.55f)
    )
    val sparks = 16
    val turn = PI.toFloat() * 2f

    field.forEachIndexed { index, shell ->
        val journey = ((t * (0.7f + shell.speed * 0.5f)) + shell.phase) % 1f
        // Most of the loop is empty sky. A firework that is always mid-burst is
        // a pattern; one that has to be waited for is a firework.
        if (journey > 0.62f) return@forEachIndexed

        val life = journey / 0.62f
        // Out fast, then slowing — a cubic ease-out, which is what an explosion
        // in air actually does.
        val spread = 1f - (1f - life) * (1f - life) * (1f - life)
        val reach = size.minDimension * (0.10f + shell.size * 3.2f) * spread
        val cx = size.width * shell.x
        val cy = size.height * (0.12f + shell.sway * 4.5f)
        val colour = colours[index % colours.size]
        // Linear, not squared. Squared looked physically right and read as
        // nothing at all — the sparks were gone before the burst had finished
        // opening, leaving a grey smudge.
        val fade = 1f - life

        repeat(sparks) { i ->
            // Offset per shell so the sparks are not all on the same spokes.
            val angle = (i / sparks.toFloat() + shell.phase * 0.3f) * turn
            // Gravity takes over as the spread runs out of speed.
            val sag = size.minDimension * 0.16f * life * life
            val px = cx + cos(angle) * reach
            val py = cy + sin(angle) * reach + sag

            // A short trail rather than a dot: the spark is moving, and a line
            // pointing back at the burst is what says so.
            drawLine(
                color = colour.copy(alpha = (0.55f + shell.alpha * 0.45f) * fade),
                start = Offset(cx + cos(angle) * reach * 0.82f, cy + sin(angle) * reach * 0.82f + sag * 0.8f),
                end = Offset(px, py),
                strokeWidth = size.minDimension * 0.009f
            )
        }
        // The flash at the centre, gone almost at once.
        if (life < 0.18f) {
            drawCircle(
                color = lerp(colour, Color.White, 0.7f).copy(alpha = (1f - life / 0.18f) * 0.5f),
                radius = size.minDimension * 0.05f * (1f - life / 0.18f),
                center = Offset(cx, cy)
            )
        }
    }
}

// ------------------------------------------------------------------ rain ----

/**
 * Rain falling hard, and rings where it lands.
 *
 * Fast and near-vertical, which is the whole difference between this and the
 * snow: the same direction of travel read completely differently once the
 * shapes are stretched into streaks and the speed is tripled.
 */
private fun DrawScope.drawRain(field: List<Mote>, t: Float, accent: Color, dark: Boolean) {
    val drop = if (dark) lerp(accent, Color.White, 0.35f) else accent
    val slant = size.height * 0.035f

    field.forEach { mote ->
        // Three times round the loop per turn of the clock — rain is the fastest
        // thing in the set.
        val journey = ((t * 3f * mote.speed) + mote.phase) % 1f
        val y = size.height * (journey * 1.25f - 0.12f)
        val x = size.width * mote.x + slant * journey
        val length = size.height * (0.02f + mote.size * 0.6f)

        drawLine(
            color = drop.copy(alpha = mote.alpha * 0.55f),
            start = Offset(x, y),
            end = Offset(x + slant * 0.25f, y + length),
            strokeWidth = (size.minDimension * mote.size * 0.09f).coerceAtLeast(1.5f)
        )
    }

    // Rings on the ground. Far fewer than there are drops, because one ring per
    // drop is a puddle boiling rather than rain landing.
    field.take(7).forEachIndexed { index, mote ->
        val ripple = ((t * 2.2f) + index * 0.17f) % 1f
        val radius = size.minDimension * 0.09f * ripple
        if (radius <= 0.5f) return@forEachIndexed
        drawCircle(
            color = drop.copy(alpha = (1f - ripple) * 0.28f),
            radius = radius,
            center = Offset(size.width * mote.x, size.height * (0.93f + mote.sway)),
            style = Stroke(width = size.minDimension * 0.004f)
        )
    }
}

// -------------------------------------------------------------- lava lamp ----

/**
 * Slow blobs rising, sinking and swelling.
 *
 * The counterweight to the rest of the set: no particles, no direction, nothing
 * to track. Big soft radial gradients that overlap and separate, on a loop long
 * enough that you never catch one starting. Fireflies failed at small warm dots
 * on a dark ground because they read as dirt on the glass; the fix is that these
 * are enormous — a fifth of the screen across — and so can only read as blobs.
 */
private fun DrawScope.drawLava(field: List<Mote>, t: Float, accent: Color) {
    val turn = PI.toFloat() * 2f
    val hot = lerp(accent, Color(0xFFFFC24D), 0.45f)

    field.forEachIndexed { index, blob ->
        // Each on its own slow rise and fall, never in step with its neighbours.
        val rise = sin((t * (0.35f + blob.speed * 0.3f) + blob.phase) * turn)
        val drift = sin((t * 0.4f + blob.phase * 3f) * turn)

        val cx = size.width * (blob.x + drift * blob.sway * 1.5f)
        val cy = size.height * (0.5f - rise * 0.42f)
        // Swelling out of phase with the rise, so the shape is never quite the
        // same twice through the loop.
        val radius = size.minDimension * (0.13f + blob.size * 1.6f) *
            (0.85f + 0.15f * sin((t * 0.9f + blob.phase * 5f) * turn))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(accent, hot, index / field.size.toFloat())
                        .copy(alpha = blob.alpha * 0.5f),
                    accent.copy(alpha = 0f)
                ),
                center = Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
    }
}

// --------------------------------------------------------------- bubbles ----

/**
 * Bubbles rising through water.
 *
 * Hearts also rise, so the difference has to be in the drawing rather than the
 * path: these are outlines with a highlight rather than filled shapes, and they
 * vary enormously in size, which is what water does and a field of identical
 * shapes does not.
 */
private fun DrawScope.drawBubbles(field: List<Mote>, t: Float, accent: Color) {
    val skin = lerp(accent, Color.White, 0.3f)

    field.forEach { mote ->
        val journey = ((t * mote.speed * 0.8f) + mote.phase) % 1f
        val y = size.height * (1.08f - journey * 1.18f)
        // Bubbles wobble quickly and narrowly; they do not swing.
        val wobble = sin((journey * 6f + mote.phase * 8f) * PI.toFloat() * 2f) * mote.sway * 0.5f
        val x = size.width * (mote.x + wobble)
        // A wide spread of sizes, so it reads as depth rather than a pattern.
        val radius = size.minDimension * mote.size * (0.5f + mote.phase * 2.2f)

        val fade = if (journey > 0.82f) (1f - journey) / 0.18f else 1f

        drawCircle(
            color = skin.copy(alpha = mote.alpha * fade * 0.55f),
            radius = radius,
            center = Offset(x, y),
            style = Stroke(width = (radius * 0.10f).coerceAtLeast(1.4f))
        )
        // The catchlight, up and to the left, which is the thing that makes a
        // ring read as a sphere.
        drawCircle(
            color = Color.White.copy(alpha = mote.alpha * fade * 0.45f),
            radius = radius * 0.16f,
            center = Offset(x - radius * 0.36f, y - radius * 0.38f)
        )
    }
}

// -------------------------------------------------------- constellations ----

/**
 * Stars that find each other and let go again.
 *
 * A network rather than a field: the points drift on their own slow paths, and
 * a line is drawn between any two that come close, fading in as they approach
 * and out as they part. Nothing else in the set draws a relationship between
 * its shapes, which is what makes this one read differently at a glance.
 */
private fun DrawScope.drawConstellations(field: List<Mote>, t: Float, accent: Color) {
    val turn = PI.toFloat() * 2f
    val star = lerp(accent, Color.White, 0.4f)
    // Anything closer than this gets a line. Kept tight, so the sky is mostly
    // unjoined and a link means something when it appears.
    val reach = size.minDimension * 0.28f

    val points = field.mapIndexed { index, mote ->
        val a = (t * mote.speed * 0.35f + mote.phase) * turn
        Offset(
            x = size.width * (mote.x + sin(a) * mote.sway * 2.2f),
            // Spread down the screen by index rather than by the random phase.
            // Left to chance, fourteen points clustered in the top third and
            // the rest of the sky was empty.
            y = size.height * ((index + 0.5f) / field.size * 0.86f + 0.07f +
                cos(a * 0.8f) * mote.sway * 1.6f)
        )
    }

    for (i in points.indices) {
        for (j in i + 1 until points.size) {
            val dx = points[i].x - points[j].x
            val dy = points[i].y - points[j].y
            val gap = kotlin.math.sqrt(dx * dx + dy * dy)
            if (gap >= reach) continue
            drawLine(
                color = star.copy(alpha = (1f - gap / reach) * 0.30f),
                start = points[i],
                end = points[j],
                strokeWidth = size.minDimension * 0.0022f
            )
        }
    }

    points.forEachIndexed { index, at ->
        val mote = field[index]
        // A slow twinkle, so the stars are never all the same brightness.
        val twinkle = 0.6f + 0.4f * ((sin((t * 4f + mote.phase * 7f) * PI.toFloat()) + 1f) / 2f)
        val radius = size.minDimension * mote.size * 0.22f
        drawCircle(star.copy(alpha = mote.alpha * twinkle * 0.25f), radius * 3f, at)
        drawCircle(star.copy(alpha = (0.55f + mote.alpha * 0.45f) * twinkle), radius, at)
    }
}

// -------------------------------------------------------------- synthwave ----

/**
 * A grid running to the horizon, under a banded sun.
 *
 * The one piece of perspective in the set. Everything else is flat and drifting;
 * this has a vanishing point, and the horizontal lines accelerate towards the
 * bottom of the screen so the ground appears to rush at the viewer. Nothing
 * moves across the screen at all — the illusion is entirely in the spacing.
 */
private fun DrawScope.drawSynthwave(t: Float, accent: Color, dark: Boolean, ground: Color) {
    val horizon = size.height * 0.58f
    val depth = size.height - horizon
    val centre = size.width / 2f
    val neon = accent
    val cyan = lerp(accent, Color(0xFF4DE8F0), 0.75f)

    // The sun: a disc sitting on the horizon, sliced by bars in the ground
    // colour. Drawing the gaps rather than the bars keeps it one shape.
    val sunRadius = size.minDimension * 0.20f
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(lerp(neon, Color(0xFFFFC24D), 0.6f), neon),
            startY = horizon - sunRadius * 2f,
            endY = horizon
        ),
        radius = sunRadius,
        center = Offset(centre, horizon - sunRadius * 0.35f)
    )
    // Slices, widening towards the bottom of the disc.
    for (i in 0..6) {
        val f = i / 6f
        val y = horizon - sunRadius * 0.9f + f * sunRadius * 1.2f
        drawRect(
            color = ground,
            topLeft = Offset(centre - sunRadius, y),
            size = androidx.compose.ui.geometry.Size(sunRadius * 2f, sunRadius * 0.035f * (1f + f * 4f))
        )
    }
    // And everything below the horizon belongs to the grid, not the sun.
    drawRect(
        color = ground,
        topLeft = Offset(0f, horizon),
        size = androidx.compose.ui.geometry.Size(size.width, depth)
    )

    val line = (size.minDimension * 0.0028f).coerceAtLeast(1.2f)

    // Verticals, all converging on the vanishing point.
    for (k in -7..7) {
        val atBottom = centre + k * size.width * 0.17f
        drawLine(
            color = cyan.copy(alpha = 0.32f),
            start = Offset(centre, horizon),
            end = Offset(atBottom, size.height),
            strokeWidth = line
        )
    }

    // Horizontals. The squared term is the perspective: rows bunch up at the
    // horizon and stretch apart as they come forward.
    val rows = 12
    val scroll = (t * 3f) % 1f
    for (i in 0 until rows) {
        val z = (i + scroll) / rows
        val y = horizon + depth * z * z
        drawLine(
            color = cyan.copy(alpha = 0.10f + 0.3f * z),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = line
        )
    }

    // A glow along the horizon itself, which is what sells it as a light source
    // rather than a line drawing.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(neon.copy(alpha = 0f), neon.copy(alpha = if (dark) 0.35f else 0.2f), neon.copy(alpha = 0f)),
            startY = horizon - size.height * 0.05f,
            endY = horizon + size.height * 0.05f
        ),
        topLeft = Offset(0f, horizon - size.height * 0.05f),
        size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.1f)
    )
}

// -------------------------------------------------------------- contours ----

/**
 * A contour map, breathing.
 *
 * Nested closed loops, each one a circle pushed out of round by a couple of
 * sine waves that turn slowly and at different rates per ring. Line art rather
 * than shapes or particles, which is the whole point of it — it is the only
 * thing in the set that draws nothing solid at all.
 */
private fun DrawScope.drawContours(t: Float, accent: Color, dark: Boolean) {
    val turn = PI.toFloat() * 2f
    val ink = lerp(accent, if (dark) Color.White else Color.Black, 0.15f)
    val rings = 11
    val steps = 90
    // Off-centre, and low. Centred it reads as a target.
    val cx = size.width * 0.38f
    val cy = size.height * 0.62f
    val step = size.minDimension * 0.085f

    for (ring in 0 until rings) {
        val base = step * (ring + 1.2f)
        // Outer rings wander further and turn slower, the way real ground does.
        val wander = size.minDimension * (0.012f + ring * 0.004f)
        val spin = t * (0.5f - ring * 0.02f)

        val path = Path()
        for (i in 0..steps) {
            val angle = i / steps.toFloat() * turn
            val r = base +
                sin(angle * 3f + (spin + ring * 0.21f) * turn) * wander +
                sin(angle * 5f - (spin * 1.6f) * turn) * wander * 0.55f
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r * 1.25f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawPath(
            path = path,
            color = ink.copy(alpha = 0.34f - ring * 0.02f),
            style = Stroke(width = (size.minDimension * 0.0035f).coerceAtLeast(1.2f))
        )
    }
}

// -------------------------------------------------------------- terminal ----

/**
 * Characters raining down a terminal.
 *
 * The glyphs are measured once and cached, then drawn by reference — laying out
 * text per frame for thirty columns would be the one genuinely expensive thing
 * in this file. ASCII rather than katakana on purpose: half-width kana is the
 * iconic choice, but a phone without CJK fonts renders the whole screen as
 * tofu boxes, and a theme that breaks on some devices is not worth the look.
 */
private const val GLYPHS = "0123456789ABCDEF#%&*<>/[]{}=+~^!?|:;"

private fun DrawScope.drawTerminal(
    t: Float,
    accent: Color,
    dark: Boolean,
    glyphs: List<TextLayoutResult>
) {
    if (glyphs.isEmpty()) return

    val cell = glyphs[0].size.height.toFloat()
    val step = glyphs[0].size.width.toFloat() * 1.6f
    if (cell <= 0f || step <= 0f) return

    val columns = (size.width / step).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    // The leading character is the bright one; everything above it is the tail
    // burning out behind it.
    val head = lerp(accent, Color.White, if (dark) 0.75f else 0.35f)
    val trail = 16

    for (col in 0 until columns) {
        // Deterministic per column, so the rain is the same every launch rather
        // than reshuffling. Cheap hash instead of a Random, because this runs
        // inside the draw.
        // A proper avalanche. Multiply-and-shift alone left neighbouring
        // columns with near-neighbouring phases, so the rain fell in one
        // diagonal band instead of scattered across the width.
        var hash = col * -0x61c88647
        hash = hash xor (hash ushr 15)
        hash *= -0x7a143595
        hash = hash xor (hash ushr 13)
        val speed = 0.55f + ((hash ushr 3) and 7) * 0.16f
        val phase = ((hash ushr 7) and 255) / 255f
        val length = trail - ((hash ushr 11) and 5)

        val lead = (((t * speed) + phase) % 1f) * (rows + length)

        for (i in 0 until length) {
            val row = lead.toInt() - i
            if (row < 0 || row >= rows) continue

            // Fades going up the tail, and the whole column dims as it leaves.
            val fade = (1f - i / length.toFloat())
            val alpha = if (i == 0) 0.95f else fade * fade * 0.55f
            if (alpha < 0.02f) continue

            // Which glyph is showing changes over time, so the tail flickers
            // rather than scrolling a fixed string down the screen.
            val churn = (t * 9f).toInt()
            val pick = ((row * 31 + col * 17 + churn * (1 + (i and 3))) % glyphs.size + glyphs.size) % glyphs.size

            drawText(
                textLayoutResult = glyphs[pick],
                color = if (i == 0) head else accent,
                topLeft = Offset(col * step, row * cell),
                alpha = alpha
            )
        }
    }
}

// ---------------------------------------------------------------- orbits ----

/**
 * The solar system, to scale in order but not in size.
 *
 * Eight planets in their real sequence, with their real colours and a period
 * that falls off with distance the way Kepler's third law says it should —
 * Neptune barely moves while Mercury laps the star. Distances and radii are
 * compressed hard, because at true scale Neptune is off the phone and Mercury
 * is one pixel.
 *
 * They share one tilt rather than each having their own, which is both true and
 * the thing that makes it read as a disc seen edge-on instead of a pile of
 * unrelated rings.
 */
private class Planet(
    /** Semi-major axis, as a fraction of the shorter screen edge. */
    val orbit: Float,
    /** Radius, same units. */
    val radius: Float,
    /** Turns of its orbit per turn of the shared clock. */
    val speed: Float,
    /** Where it starts on its orbit, 0..1. Without this they all launch
     *  lined up on one side, and the slow outer ones stay that way for ages. */
    val phase: Float,
    val colour: Color,
    /** Saturn, and only Saturn. */
    val ringed: Boolean = false
)

private val SOLAR_SYSTEM = listOf(
    // Mercury: small, cratered, grey-brown.
    Planet(orbit = 0.085f, radius = 0.0075f, speed = 1.60f, phase = 0.00f, colour = Color(0xFF9C8B7A)),
    // Venus: cloud deck, pale cream-yellow.
    Planet(orbit = 0.125f, radius = 0.0110f, speed = 0.90f, phase = 0.36f, colour = Color(0xFFE8CFA0)),
    // Earth.
    Planet(orbit = 0.170f, radius = 0.0115f, speed = 0.57f, phase = 0.62f, colour = Color(0xFF4A90D9)),
    // Mars: iron oxide.
    Planet(orbit = 0.215f, radius = 0.0090f, speed = 0.40f, phase = 0.15f, colour = Color(0xFFC1440E)),
    // Jupiter: banded tan.
    Planet(orbit = 0.285f, radius = 0.0210f, speed = 0.26f, phase = 0.81f, colour = Color(0xFFD8A56B)),
    // Saturn: paler gold, and the rings.
    Planet(orbit = 0.350f, radius = 0.0180f, speed = 0.19f, phase = 0.44f, colour = Color(0xFFE3C88A), ringed = true),
    // Uranus: methane cyan.
    Planet(orbit = 0.410f, radius = 0.0140f, speed = 0.15f, phase = 0.07f, colour = Color(0xFF9FD8E0)),
    // Neptune: deeper blue than Uranus, and slower than everything.
    Planet(orbit = 0.462f, radius = 0.0135f, speed = 0.126f, phase = 0.69f, colour = Color(0xFF4166C9))
)

private fun DrawScope.drawOrbits(t: Float, accent: Color) {
    val turn = PI.toFloat() * 2f
    val centre = Offset(size.width * 0.5f, size.height * 0.44f)
    val line = (size.minDimension * 0.0022f).coerceAtLeast(1f)
    // One tilt and one flattening for the whole system: the planets really are
    // close to coplanar, and drawing them that way is also what makes it read.
    val tilt = -17f
    val flatten = 0.34f

    // The sun.
    val corona = size.minDimension * 0.155f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFF3C4).copy(alpha = 0.55f),
                Color(0xFFFFB23F).copy(alpha = 0.18f),
                accent.copy(alpha = 0f)
            ),
            center = centre,
            radius = corona
        ),
        radius = corona,
        center = centre
    )
    drawCircle(Color(0xFFFFF6D8).copy(alpha = 0.95f), size.minDimension * 0.026f, centre)

    rotate(degrees = tilt, pivot = centre) {
        SOLAR_SYSTEM.forEach { planet ->
            val major = size.minDimension * planet.orbit
            val minor = major * flatten

            drawOval(
                color = accent.copy(alpha = 0.14f),
                topLeft = Offset(centre.x - major, centre.y - minor),
                size = Size(major * 2f, minor * 2f),
                style = Stroke(width = line)
            )

            val angle = (t * planet.speed + planet.phase) * turn
            val at = Offset(centre.x + cos(angle) * major, centre.y + sin(angle) * minor)
            val r = size.minDimension * planet.radius

            if (planet.ringed) {
                // Drawn before the body so the near side of the ring does not
                // sit on top of the planet it belongs to.
                drawOval(
                    color = planet.colour.copy(alpha = 0.5f),
                    topLeft = Offset(at.x - r * 2.1f, at.y - r * 0.62f),
                    size = Size(r * 4.2f, r * 1.24f),
                    style = Stroke(width = line * 1.6f)
                )
            }

            // A soft halo, then the disc. A flat circle for the glow gives a
            // hard grey edge instead of light.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(planet.colour.copy(alpha = 0.30f), planet.colour.copy(alpha = 0f)),
                    center = at,
                    radius = r * 2.6f
                ),
                radius = r * 2.6f,
                center = at
            )
            drawCircle(planet.colour, r, at)
        }
    }
}

// ---------------------------------------------------------------- petals ----

/**
 * Blossom coming down.
 *
 * The leaves theme also falls and turns, so the difference here is the flutter:
 * each petal is squashed horizontally by the cosine of its own spin, so it
 * turns edge-on and vanishes to a line before opening out again. That, plus a
 * much slower descent, is what separates blossom from autumn.
 */
private fun DrawScope.drawPetals(field: List<Mote>, t: Float, accent: Color) {
    val turn = PI.toFloat() * 2f
    val tints = listOf(
        accent,
        lerp(accent, Color.White, 0.55f),
        lerp(accent, Color(0xFFFFD9E4), 0.6f),
        lerp(accent, Color(0xFFE08BA8), 0.4f)
    )

    field.forEachIndexed { index, mote ->
        val journey = ((t * mote.speed * 0.5f) + mote.phase) % 1f
        val y = size.height * (journey * 1.2f - 0.1f)
        val swing = sin((journey * 2.4f + mote.phase * 5f) * turn) * mote.sway * 2.6f
        val x = size.width * (mote.x + swing)

        val spin = (t * (1.2f + mote.speed) + mote.phase) * turn
        // Edge-on when the cosine passes through zero, which is the flutter.
        val face = cos(spin)
        val length = size.minDimension * mote.size * 1.6f

        rotate(degrees = journey * 200f + mote.phase * 360f, pivot = Offset(x, y)) {
            scale(scaleX = face.coerceIn(-1f, 1f), scaleY = 1f, pivot = Offset(x, y)) {
                drawPath(
                    path = petalPath(x, y, length * 0.62f, length),
                    // A floor under the opacity: pale pink at a quarter alpha on a
                    // near-black ground is not pale pink, it is brown.
                    color = tints[index % tints.size].copy(alpha = (0.55f + mote.alpha * 0.45f) * 0.85f)
                )
            }
        }
    }
}

/** A rounded petal: wide and blunt at the tip, pinched at the stem. */
private fun petalPath(cx: Float, cy: Float, w: Float, h: Float): Path = Path().apply {
    moveTo(cx, cy + h / 2f)
    cubicTo(cx - w, cy + h * 0.1f, cx - w * 0.75f, cy - h * 0.5f, cx, cy - h / 2f)
    cubicTo(cx + w * 0.75f, cy - h * 0.5f, cx + w, cy + h * 0.1f, cx, cy + h / 2f)
    close()
}

// ---------------------------------------------------------------- hearts ----

/** Hearts drifting up the page, swaying, fading out before they reach the top. */
private fun DrawScope.drawHearts(field: List<Mote>, t: Float, accent: Color) {
    field.forEach { mote ->
        val journey = ((t * mote.speed) + mote.phase) % 1f
        val y = size.height * (1.1f - journey * 1.2f)
        val wobble = sin((journey * 3f + mote.phase * 6f) * PI.toFloat()) * mote.sway
        val x = size.width * (mote.x + wobble)

        // In at the bottom, out at the top, solid through the middle.
        val fade = when {
            journey < 0.15f -> journey / 0.15f
            journey > 0.75f -> (1f - journey) / 0.25f
            else -> 1f
        }
        val s = size.minDimension * mote.size

        drawPath(
            path = heartPath(x, y, s),
            color = accent.copy(alpha = mote.alpha * fade * 0.5f)
        )
    }
}

private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.42f)
    cubicTo(cx - s * 1.0f, cy - s * 0.24f, cx - s * 0.44f, cy - s * 0.86f, cx, cy - s * 0.34f)
    cubicTo(cx + s * 0.44f, cy - s * 0.86f, cx + s * 1.0f, cy - s * 0.24f, cx, cy + s * 0.42f)
    close()
}

// ------------------------------------------------------------------ snow ----

/** Six-armed crystals falling, swaying and turning as they go. */
private fun DrawScope.drawSnow(field: List<Mote>, t: Float, accent: Color, dark: Boolean) {
    // White reads as snow on a dark night; on a pale ground it vanishes, so
    // there the flake takes the accent and only leans towards white.
    val flake = if (dark) Color.White else lerp(accent, Color.White, 0.35f)

    field.forEach { mote ->
        val journey = ((t * mote.speed) + mote.phase) % 1f
        val y = size.height * (journey * 1.15f - 0.08f)
        val wobble = sin((journey * 2.5f + mote.phase * 7f) * PI.toFloat() * 2f) * mote.sway
        val x = size.width * (mote.x + wobble)

        // Turning slowly, each on its own, so the field never rotates as a unit.
        val spin = (t * mote.speed * 0.6f + mote.phase) * PI.toFloat() * 2f
        val radius = size.minDimension * mote.size * 0.62f

        drawFlake(
            cx = x,
            cy = y,
            radius = radius,
            spin = spin,
            color = flake.copy(alpha = mote.alpha * (if (dark) 0.95f else 0.7f)),
            // Thin enough to read as a crystal rather than a snowball, but
            // never below a pixel or it disappears on the smaller flakes.
            stroke = (radius * 0.13f).coerceAtLeast(1.6f)
        )
    }
}

/**
 * A snowflake: six arms from a centre, each with a pair of branches.
 *
 * Strokes rather than a filled shape, because a crystal is mostly gaps — filled
 * is how the previous version ended up looking like a ball of snow instead.
 */
private fun DrawScope.drawFlake(
    cx: Float,
    cy: Float,
    radius: Float,
    spin: Float,
    color: Color,
    stroke: Float
) {
    val centre = Offset(cx, cy)
    repeat(6) { arm ->
        val angle = spin + arm * (PI.toFloat() / 3f)
        val tip = Offset(cx + cos(angle) * radius, cy + sin(angle) * radius)
        drawLine(color, centre, tip, strokeWidth = stroke)

        // One pair of branches, two thirds of the way out.
        val fork = Offset(cx + cos(angle) * radius * 0.62f, cy + sin(angle) * radius * 0.62f)
        listOf(-1f, 1f).forEach { side ->
            val branch = angle + side * (PI.toFloat() / 4.5f)
            drawLine(
                color = color,
                start = fork,
                end = Offset(
                    fork.x + cos(branch) * radius * 0.34f,
                    fork.y + sin(branch) * radius * 0.34f
                ),
                strokeWidth = stroke * 0.8f
            )
        }
    }
}

// ---------------------------------------------------------------- leaves ----

/**
 * Leaves coming down: falling, swinging, and turning over as they go.
 *
 * This replaced a field of fireflies, which was a green smudge that read as
 * dirt on the screen. Rotation is the point of this one — it is the only motion
 * in the set where the shapes turn, which is what stops a seventh drifting
 * particle field from looking like the six before it.
 */
private fun DrawScope.drawLeaves(field: List<Mote>, t: Float, accent: Color) {
    // Rust, amber and gold. Real leaves are not all one colour and a single
    // tint here looked like confetti cut from one sheet of paper.
    val tints = listOf(
        accent,
        lerp(accent, Color(0xFFFFC46B), 0.55f),
        lerp(accent, Color(0xFF8C3B12), 0.5f),
        lerp(accent, Color(0xFFD9752E), 0.6f)
    )

    field.forEachIndexed { index, mote ->
        val journey = ((t * mote.speed * 0.7f) + mote.phase) % 1f
        val y = size.height * (journey * 1.2f - 0.1f)
        // A wide, slow swing rather than a shiver — a leaf falls in arcs.
        val swing = sin((journey * 2f + mote.phase * 5f) * PI.toFloat() * 2f) * mote.sway * 2.2f
        val x = size.width * (mote.x + swing)

        val length = size.minDimension * mote.size * 1.7f
        // Turns as it falls, and the swing leads the turn, so it looks like the
        // air is doing it rather than a motor.
        val spin = (journey * 2.4f + mote.phase) * 360f + swing * 900f

        rotate(degrees = spin, pivot = Offset(x, y)) {
            drawPath(
                path = leafPath(x, y, length * 0.52f, length),
                color = tints[index % tints.size].copy(alpha = mote.alpha * 0.75f)
            )
        }
    }
}

/** A pointed oval with a stem: two arcs meeting at tip and base. */
private fun leafPath(cx: Float, cy: Float, w: Float, h: Float): Path = Path().apply {
    moveTo(cx, cy - h / 2f)
    quadraticTo(cx + w, cy, cx, cy + h / 2f)
    quadraticTo(cx - w, cy, cx, cy - h / 2f)
    close()
}

// ---------------------------------------------------------------- aurora ----

/**
 * Not particles: a few broad curtains of light that lean and breathe.
 *
 * Deliberately the odd one out. The others are a scatter of small shapes, and
 * another of those would have been a further way of saying the same thing.
 */
private fun DrawScope.drawAurora(field: List<Mote>, t: Float, accent: Color) {
    val tint = listOf(
        accent,
        lerp(accent, Color(0xFF64FFDA), 0.55f),
        lerp(accent, Color(0xFF7C4DFF), 0.45f),
        lerp(accent, Color.White, 0.3f)
    )

    field.forEachIndexed { index, band ->
        val drift = sin((t * band.speed + band.phase) * PI.toFloat() * 2f)
        val top = size.height * (0.08f + index * 0.17f) + drift * size.height * 0.05f
        val depth = size.height * (0.16f + band.size * 2.2f)

        val path = Path().apply {
            moveTo(0f, top)
            // Two humps across the width, sliding as the clock turns.
            val lift = size.height * 0.06f
            cubicTo(
                size.width * 0.3f, top - lift * (1f + drift),
                size.width * 0.7f, top + lift * (1f - drift),
                size.width, top + drift * lift
            )
            lineTo(size.width, top + depth + drift * lift)
            cubicTo(
                size.width * 0.7f, top + depth + lift * (1f - drift),
                size.width * 0.3f, top + depth - lift * (1f + drift),
                0f, top + depth
            )
            close()
        }

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    tint[index % tint.size].copy(alpha = 0f),
                    tint[index % tint.size].copy(alpha = band.alpha * 0.28f),
                    tint[index % tint.size].copy(alpha = 0f)
                ),
                // Bounded to the band, so the fade belongs to this curtain
                // rather than being measured against the whole canvas.
                startY = top,
                endY = top + depth
            )
        )
    }
}

// ------------------------------------------------------------------ bats ----

/**
 * Bats crossing the page, wings beating.
 *
 * They fly right to left on their own paths and bob as they go, and the ones
 * further away are smaller, fainter and slower — which is what gives the sky
 * depth rather than one flat row of shapes.
 */
private fun DrawScope.drawBats(field: List<Mote>, t: Float, accent: Color) {
    // Bats are a silhouette, not a colour: near-black with a trace of the
    // palette's orange, so they read as shapes cut out of the sky. This only
    // works because the Halloween palette's night is a dusk purple rather than
    // the usual near-black — against that, the first version of these was
    // invisible, a black shape on a black ground.
    val bat = lerp(accent, Color.Black, 0.86f)

    field.forEachIndexed { index, mote ->
        val journey = ((t * mote.speed * 0.8f) + mote.phase) % 1f
        // Right to left, starting and ending clear of the edges.
        val x = size.width * (1.15f - journey * 1.3f)
        val bob = sin((journey * 3.5f + mote.phase * 4f) * PI.toFloat() * 2f) * mote.sway * 3f
        val y = size.height * (0.12f + mote.x * 0.7f + bob)

        // Depth: the near ones are bigger, darker and beat faster.
        val near = 0.45f + (index % 3) * 0.3f
        val span = size.minDimension * mote.size * 4.2f * near

        // Wing beat. Fast enough to read as flapping, not as a wobble.
        val flap = sin((t * 26f + mote.phase * 13f) * PI.toFloat())

        // A floor under the opacity as well as a ceiling. A silhouette that
        // fades out stops being a bat and becomes a smudge, so the faintest one
        // here is still clearly a shape.
        drawPath(
            path = batPath(x, y, span, flap),
            color = bat.copy(alpha = (0.62f + mote.alpha * 0.38f) * near.coerceAtMost(1f))
        )
    }
}

/**
 * A bat silhouette: a small body between two scalloped wings.
 *
 * [flap] runs -1..1 and lifts or drops the wing tips only; the body stays put,
 * so the shape beats rather than bounces.
 */
private fun batPath(cx: Float, cy: Float, span: Float, flap: Float): Path = Path().apply {
    val w = span / 2f
    val h = span * 0.45f
    // Wing tips rise and fall; the shoulders follow at half the throw, so the
    // wing bends along its length instead of tilting like a plank.
    val tip = h * 0.34f * flap

    fun x(f: Float) = cx + w * f
    fun y(f: Float) = cy + h * f

    moveTo(cx, y(0.22f))
    // Right trailing edge: two scallops out to the tip. The scallops are what
    // make it a bat — a smooth wing edge reads as a bird, or as a leaf.
    cubicTo(x(0.18f), y(0.30f), x(0.30f), y(0.05f), x(0.42f), y(0.16f))
    cubicTo(x(0.58f), y(0.30f), x(0.75f), y(0.08f), x(1f), y(-0.10f) - tip)
    // Right leading edge, back in to the shoulder.
    cubicTo(x(0.80f), y(-0.34f) - tip * 0.6f, x(0.45f), y(-0.30f), x(0.20f), y(-0.24f))
    // Head: an ear, the dome of the skull, the other ear.
    lineTo(x(0.16f), y(-0.46f))
    lineTo(x(0.10f), y(-0.30f))
    cubicTo(x(0.05f), y(-0.38f), x(-0.05f), y(-0.38f), x(-0.10f), y(-0.30f))
    lineTo(x(-0.16f), y(-0.46f))
    lineTo(x(-0.20f), y(-0.24f))
    // And the left wing, mirrored.
    cubicTo(x(-0.45f), y(-0.30f), x(-0.80f), y(-0.34f) - tip * 0.6f, x(-1f), y(-0.10f) - tip)
    cubicTo(x(-0.75f), y(0.08f), x(-0.58f), y(0.30f), x(-0.42f), y(0.16f))
    cubicTo(x(-0.30f), y(0.05f), x(-0.18f), y(0.30f), cx, y(0.22f))
    close()
}

// ----------------------------------------------------------- fairy lights ----

private val BULB_COLOURS = listOf(
    Color(0xFFE23B3B), // red
    Color(0xFF3FBF5F), // green
    Color(0xFFFFC93C), // gold
    Color(0xFF4FA3E3), // blue
    Color(0xFFFF8AC4)  // pink
)

/**
 * Strings of lights hung across the page, swaying, bulbs twinkling.
 *
 * The other seasonal themes are weather — things falling or flying past. This
 * one is furniture: it hangs off the top of the page and stays there, and the
 * only thing that moves is the sag of the wire and which bulbs are lit. That
 * makes it the calmest of the set, which is what you want from the one people
 * are most likely to leave switched on for a month.
 */
private fun DrawScope.drawFairyLights(t: Float, accent: Color, dark: Boolean) {
    val wire = lerp(accent, if (dark) Color.Black else Color(0xFF4A3B2A), 0.55f)
    val strands = 3

    repeat(strands) { strand ->
        // Each strand hangs lower than the one before and sways on its own beat.
        val anchor = size.height * (0.05f + strand * 0.12f)
        val sway = sin((t * (1.1f + strand * 0.35f) + strand * 0.4f) * PI.toFloat() * 2f)
        val sag = size.height * (0.10f + strand * 0.02f) + sway * size.height * 0.018f

        // One quadratic: both ends pinned off-screen, the control point pulled
        // down by the sag. A hanging wire is near enough to this to not need
        // the real catenary.
        val start = Offset(-size.width * 0.05f, anchor)
        val control = Offset(size.width * (0.5f + sway * 0.05f), anchor + sag * 2f)
        val end = Offset(size.width * 1.05f, anchor + size.height * 0.02f)

        drawPath(
            path = Path().apply {
                moveTo(start.x, start.y)
                quadraticTo(control.x, control.y, end.x, end.y)
            },
            color = wire.copy(alpha = 0.5f),
            style = Stroke(width = size.minDimension * 0.004f)
        )

        val bulbs = 9
        for (i in 0..bulbs) {
            val s = i / bulbs.toFloat()
            val at = quadraticPoint(start, control, end, s)
            val colour = BULB_COLOURS[(i + strand * 2) % BULB_COLOURS.size]

            // Each bulb breathes on its own offset, so the string twinkles
            // instead of pulsing as one piece.
            val pulse = (sin((t * 7f + i * 1.7f + strand * 2.3f) * PI.toFloat()) + 1f) / 2f
            val lit = 0.35f + pulse * 0.65f
            val radius = size.minDimension * 0.011f

            // A short lead so the bulb hangs off the wire rather than sitting on it.
            val hang = Offset(at.x, at.y + radius * 1.6f)
            drawLine(
                color = wire.copy(alpha = 0.5f),
                start = at,
                end = hang,
                strokeWidth = size.minDimension * 0.003f
            )
            // Halo first, then the bulb, so the glow sits behind the glass.
            drawCircle(colour.copy(alpha = 0.18f * lit), radius * 3.4f, hang)
            drawCircle(colour.copy(alpha = 0.85f * lit), radius, hang)
        }
    }
}

/** Point at [s] (0..1) along a quadratic Bézier. */
private fun quadraticPoint(p0: Offset, p1: Offset, p2: Offset, s: Float): Offset {
    val inv = 1f - s
    return Offset(
        x = inv * inv * p0.x + 2f * inv * s * p1.x + s * s * p2.x,
        y = inv * inv * p0.y + 2f * inv * s * p1.y + s * s * p2.y
    )
}

// ---------------------------------------------------------------- engine ----

/**
 * Three cylinders, running.
 *
 * A section through an inline triple: bores, pistons, rods and cranks, drawn as
 * a cutaway rather than as a shape. The crank angles are 120 degrees apart, so
 * one piston is always rising while another falls — which is the thing that
 * makes it read as an engine rather than as three pumps.
 *
 * The piston position is the real slider-crank solution and not a sine wave.
 * A sine wave is symmetric; a real piston spends longer near the bottom than
 * the top, and that limp is most of what an engine looks like.
 *
 * It shares a workshop with the gear train, and the two were kept apart
 * deliberately: gears are a scattered field of rotating outlines, this is one
 * machine, centred, going up and down.
 */
private fun DrawScope.drawEngine(t: Float, accent: Color, dark: Boolean) {
    val turn = PI.toFloat() * 2f
    // The machine is drawn in steel, not in the accent. Tinting the metal with
    // the accent turned the whole engine salmon and left the combustion with
    // nothing of its own to be — so the red is spent entirely on the firing.
    val metal = if (dark) Color.White else Color.Black
    val stroke = (size.minDimension * 0.005f).coerceAtLeast(1.4f)
    val ink = metal.copy(alpha = if (dark) 0.26f else 0.30f)
    val faint = metal.copy(alpha = if (dark) 0.10f else 0.13f)

    // Rod about three throws long. Much longer and the stroke is a twitch at
    // the top of a tall empty tube, which is what this looked like first.
    val throwR = size.minDimension * 0.118f
    val rodL = size.minDimension * 0.36f
    val bore = size.minDimension * 0.18f
    val crankY = size.height * 0.60f
    // The deck has to clear top dead centre, or the piston draws through it.
    val deckY = crankY - throwR - rodL - bore * 0.62f

    // One shaft under all three. Without it they are three machines rather
    // than three cylinders of the same one.
    drawLine(
        color = ink,
        start = Offset(size.width * 0.10f, crankY),
        end = Offset(size.width * 0.90f, crankY),
        strokeWidth = stroke
    )
    // The deck, for the same reason at the other end.
    drawLine(
        color = ink,
        start = Offset(size.width * 0.14f, deckY),
        end = Offset(size.width * 0.86f, deckY),
        strokeWidth = stroke
    )

    val base = t * turn * 8f

    for (cyl in 0 until 3) {
        // Spaced so the three crank circles clear each other. Overlapping them
        // put two rods on what looked like one journal.
        val cx = size.width * (0.24f + cyl * 0.26f)
        val angle = base + cyl * turn / 3f
        val offset = throwR * sin(angle)
        val pin = Offset(cx + offset, crankY - throwR * cos(angle))
        // Slider-crank: the rod is a fixed length, so the piston sits wherever
        // that length reaches from the crank pin down the bore centreline.
        val reach = sqrt((rodL * rodL - offset * offset).coerceAtLeast(0f))
        val pistonY = crankY - throwR * cos(angle) - reach

        // Combustion, every other revolution — a four-stroke fires once per two.
        // Firing on every one looked busy and, worse, looked like a mistake to
        // anyone who knows what a four-stroke is.
        val revolution = floor(angle / turn).toInt()
        val atTop = ((cos(angle) - 0.80f) / 0.20f).coerceIn(0f, 1f)
        if (revolution % 2 == 0 && atTop > 0f) {
            val chamber = Offset(cx, (deckY + pistonY) * 0.5f)
            val flare = bore * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lerp(accent, Color.White, 0.4f).copy(alpha = 0.5f * atTop),
                        accent.copy(alpha = 0f)
                    ),
                    center = chamber,
                    radius = flare
                ),
                radius = flare,
                center = chamber
            )
        }

        // The bore ends just past where the piston reaches at the bottom of its
        // travel, so the slug is never drawn hanging out of its own cylinder.
        val skirtY = crankY + throwR - rodL + bore * 0.78f + bore * 0.14f
        drawLine(ink, Offset(cx - bore / 2f, deckY), Offset(cx - bore / 2f, skirtY), strokeWidth = stroke)
        drawLine(ink, Offset(cx + bore / 2f, deckY), Offset(cx + bore / 2f, skirtY), strokeWidth = stroke)

        // The circle the crank pin travels, left on the drawing like a locus.
        drawCircle(faint, throwR, Offset(cx, crankY), style = Stroke(width = stroke * 0.8f))

        // Rod and web.
        drawLine(ink, Offset(cx, pistonY + bore * 0.78f * 0.6f), pin, strokeWidth = stroke * 1.4f)
        drawLine(ink, Offset(cx, crankY), pin, strokeWidth = stroke * 1.8f)
        drawCircle(ink, stroke * 2.2f, pin)
        drawCircle(ink, stroke * 2.6f, Offset(cx, crankY), style = Stroke(width = stroke))

        // A tall slug filling the bore, with the pin inside it. The first
        // version was a shallow box sitting on top of two long bore walls,
        // which read as a table rather than as a piston in a cylinder.
        val slug = bore * 0.78f
        drawRect(
            color = ink,
            topLeft = Offset(cx - bore * 0.44f, pistonY),
            size = Size(bore * 0.88f, slug),
            style = Stroke(width = stroke)
        )
        for (ring in 1..2) {
            val ry = pistonY + slug * 0.16f * ring
            drawLine(
                color = ink,
                start = Offset(cx - bore * 0.44f, ry),
                end = Offset(cx + bore * 0.44f, ry),
                strokeWidth = stroke * 0.7f
            )
        }
        drawCircle(ink, stroke * 2f, Offset(cx, pistonY + slug * 0.6f), style = Stroke(width = stroke))
    }
}

// --------------------------------------------------------------- thunder ----

/**
 * Cloud, and every so often a strike.
 *
 * The cloud does almost nothing. It drifts, and it is the reason the lightning
 * has somewhere to come from — a bolt out of an empty sky reads as a glitch.
 * Each strike lasts about half a second in a fourteen-second loop, and the wait
 * is what makes it land.
 *
 * Each bank is one path of overlapping ovals, filled once. Drawn as separate
 * translucent circles the overlaps stack into visible seams, which is the same
 * reason the shapes elsewhere in this file are unioned rather than layered.
 */
private fun DrawScope.drawThunder(t: Float, accent: Color, dark: Boolean) {
    val cloud = lerp(accent, if (dark) Color.Black else Color.White, if (dark) 0.5f else 0.35f)
    val bolt = lerp(accent, Color.White, if (dark) 0.7f else 0.2f)

    // Overcast rather than three stripes of cloud. Two banks, each built from
    // ovals at scattered heights and widely different sizes — an even row of
    // same-sized bumps reads as scalloped trim, which is exactly what the first
    // version of this looked like.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(cloud.copy(alpha = 0.22f), cloud.copy(alpha = 0f)),
            startY = 0f,
            endY = size.height * 0.42f
        )
    )

    for (bank in 0 until 2) {
        val baseY = size.height * (0.10f + bank * 0.09f)
        val drift = (t * (0.05f + bank * 0.035f)) % 1f
        val lumps = 21
        val path = Path()
        for (i in 0 until lumps) {
            val slot = ((i / lumps.toFloat()) + drift) % 1f
            val x = slot * (size.width * 1.25f) - size.width * 0.13f
            val r = size.minDimension * (0.045f + 0.085f * abs(sin(i * 2.3f + bank * 5f)))
            val y = baseY + sin(i * 1.7f + bank * 2f) * size.minDimension * 0.045f
            path.addOval(Rect(x - r, y - r * 0.72f, x + r, y + r * 0.72f))
        }
        drawPath(path, cloud.copy(alpha = 0.14f + bank * 0.09f))
    }

    // Two per loop, at fixed points in it. Fixed rather than random, because a
    // strike that can land twice in a row looks broken rather than stormy.
    listOf(0.17f, 0.48f, 0.79f).forEachIndexed { index, at ->
        val since = t - at
        if (since < 0f || since > 0.06f) return@forEachIndexed
        val life = since / 0.06f
        // Lightning flickers rather than fading. Three beats and out.
        val flash = when {
            life < 0.18f -> 1f
            life < 0.36f -> 0.3f
            life < 0.58f -> 0.8f
            else -> ((1f - life) / 0.42f).coerceAtLeast(0f)
        }

        // The whole sky lifting, which is most of what a strike looks like from
        // underneath one.
        drawRect(bolt.copy(alpha = 0.09f * flash))

        val fromX = size.width * (if (index == 0) 0.31f else 0.71f)
        val lean = size.width * (if (index == 0) 0.06f else -0.07f)
        val segments = 9
        val path = Path()
        path.moveTo(fromX, size.height * 0.16f)
        var forkAt = Offset(fromX, size.height * 0.16f)
        for (i in 1..segments) {
            val f = i / segments.toFloat()
            val x = fromX + sin(i * 2.9f + index * 4f) * size.width * 0.05f + lean * f
            val y = size.height * (0.16f + f * 0.62f)
            path.lineTo(x, y)
            if (i == 4) forkAt = Offset(x, y)
        }
        // One branch, because a single line is a crack and two is lightning.
        path.moveTo(forkAt.x, forkAt.y)
        path.lineTo(forkAt.x - lean * 1.4f, forkAt.y + size.height * 0.09f)
        path.lineTo(forkAt.x - lean * 2.6f, forkAt.y + size.height * 0.21f)

        drawPath(
            path = path,
            color = bolt.copy(alpha = 0.92f * flash),
            style = Stroke(width = (size.minDimension * 0.0065f).coerceAtLeast(2f))
        )
    }
}

// ----------------------------------------------------------------- gears ----

/** One wheel in the train. Position and radius are fractions of the screen. */
private data class Cog(
    val x: Float,
    val y: Float,
    val radius: Float,
    val teeth: Int,
    /** Brass rather than steel. A machine is two metals, mostly the dull one. */
    val brass: Boolean
)

private val GEAR_TRAIN = listOf(
    Cog(0.18f, 0.20f, 0.20f, 16, false),
    Cog(0.60f, 0.33f, 0.15f, 12, true),
    Cog(0.90f, 0.60f, 0.18f, 14, false),
    Cog(0.33f, 0.71f, 0.25f, 20, true),
    Cog(0.04f, 0.93f, 0.13f, 10, false)
)

/**
 * A gear train, turning.
 *
 * Line art rather than solid wheels, for the same reason the contour map is:
 * five filled discs behind a page of text is a wall, five outlines is a
 * texture. Bigger wheels turn slower and every other one turns backwards, which
 * is what meshing looks like even where the teeth are not really engaging.
 */
private fun DrawScope.drawGears(t: Float, accent: Color, dark: Boolean) {
    val turn = PI.toFloat() * 2f
    val steel = lerp(accent, if (dark) Color.White else Color.Black, if (dark) 0.4f else 0.3f)
    val stroke = (size.minDimension * 0.0042f).coerceAtLeast(1.3f)

    GEAR_TRAIN.forEachIndexed { index, cog ->
        val r = size.minDimension * cog.radius
        val cx = size.width * cog.x
        val cy = size.height * cog.y
        val ink = (if (cog.brass) accent else steel).copy(alpha = 0.28f)
        val spin = t * 360f * (2.6f / cog.teeth) * (if (index % 2 == 0) 1f else -1f)

        rotate(degrees = spin, pivot = Offset(cx, cy)) {
            drawPath(
                path = gearPath(cx, cy, r * 0.80f, r, cog.teeth),
                color = ink,
                style = Stroke(width = stroke)
            )
            // Hub, bore and spokes. Without them the outline reads as a star.
            drawCircle(ink, r * 0.30f, Offset(cx, cy), style = Stroke(width = stroke))
            drawCircle(ink, r * 0.11f, Offset(cx, cy), style = Stroke(width = stroke))
            for (spoke in 0 until 4) {
                val a = spoke / 4f * turn
                drawLine(
                    color = ink,
                    start = Offset(cx + cos(a) * r * 0.13f, cy + sin(a) * r * 0.13f),
                    end = Offset(cx + cos(a) * r * 0.77f, cy + sin(a) * r * 0.77f),
                    strokeWidth = stroke
                )
            }
        }
    }
}

/** A cog outline: [teeth] square teeth standing off a root circle. */
private fun gearPath(cx: Float, cy: Float, root: Float, tip: Float, teeth: Int): Path = Path().apply {
    val turn = PI.toFloat() * 2f
    for (i in 0 until teeth) {
        val a0 = i / teeth.toFloat() * turn
        val a1 = (i + 0.28f) / teeth * turn
        val a2 = (i + 0.62f) / teeth * turn
        val a3 = (i + 0.90f) / teeth * turn
        if (i == 0) moveTo(cx + cos(a0) * root, cy + sin(a0) * root)
        else lineTo(cx + cos(a0) * root, cy + sin(a0) * root)
        lineTo(cx + cos(a1) * tip, cy + sin(a1) * tip)
        lineTo(cx + cos(a2) * tip, cy + sin(a2) * tip)
        lineTo(cx + cos(a3) * root, cy + sin(a3) * root)
    }
    close()
}

// ---------------------------------------------------------- oscilloscope ----

/**
 * Two traces on a scope, running.
 *
 * The graticule is what makes it an instrument rather than a squiggle on graph
 * paper: divisions counted out from the centre, brighter centre axes, and minor
 * ticks along them. Without those it reads as a wave over a grid; with them it
 * reads as a screen that is measuring something.
 *
 * Each trace is drawn twice — once wide and dim, once thin and bright — which
 * is a cheap stand-in for phosphor bloom and the thing that stops a one-pixel
 * line looking like a vector drawing. The two run at different frequencies and
 * scroll in opposite directions, so they cross constantly and the picture never
 * settles into a standing pattern.
 */
private fun DrawScope.drawOscilloscope(t: Float, accent: Color, dark: Boolean) {
    val turn = PI.toFloat() * 2f
    val cy = size.height * 0.5f
    val cx = size.width * 0.5f
    val cell = size.minDimension * 0.105f
    val ink = lerp(accent, if (dark) Color.Black else Color.White, 0.6f)
    val hair = (size.minDimension * 0.0028f).coerceAtLeast(1f)

    // Counted out from the centre in both directions rather than from the top
    // left, so the axes land on the middle of the screen at any screen size.
    val cols = (size.width / cell / 2f).toInt() + 1
    for (i in -cols..cols) {
        val x = cx + i * cell
        drawLine(ink.copy(alpha = 0.26f), Offset(x, 0f), Offset(x, size.height), strokeWidth = hair)
    }
    val rows = (size.height / cell / 2f).toInt() + 1
    for (i in -rows..rows) {
        val y = cy + i * cell
        drawLine(ink.copy(alpha = 0.26f), Offset(0f, y), Offset(size.width, y), strokeWidth = hair)
    }

    drawLine(ink.copy(alpha = 0.46f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = hair * 1.5f)
    drawLine(ink.copy(alpha = 0.46f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = hair * 1.5f)

    // Five minor ticks to a division, on both axes, the way a real graticule
    // subdivides its centre lines.
    val tick = cell / 5f
    val reach = cell * 0.07f
    var tx = cx % tick
    while (tx < size.width) {
        drawLine(ink.copy(alpha = 0.40f), Offset(tx, cy - reach), Offset(tx, cy + reach), strokeWidth = hair)
        tx += tick
    }
    var ty = cy % tick
    while (ty < size.height) {
        drawLine(ink.copy(alpha = 0.40f), Offset(cx - reach, ty), Offset(cx + reach, ty), strokeWidth = hair)
        ty += tick
    }

    /**
     * One trace. [harmonic] adds a third above the fundamental, which is what
     * keeps it from being a plain sine — a textbook sine wave reads as a
     * diagram, and anything with a harmonic in it reads as a signal.
     */
    fun trace(
        amplitude: Float,
        frequency: Float,
        speed: Float,
        harmonic: Float,
        colour: Color,
        width: Float,
        alpha: Float
    ) {
        val steps = 150
        val path = Path()
        val phase = t * turn * speed
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val a = f * turn * frequency + phase
            val x = f * size.width
            val y = cy + amplitude * (sin(a) + harmonic * sin(a * 3f + phase * 0.6f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, colour.copy(alpha = alpha * 0.22f), style = Stroke(width = width * 4.5f))
        drawPath(path, colour.copy(alpha = alpha), style = Stroke(width = width))
    }

    // The second one paler and dimmer. Pale reads bright on black, so it needs
    // the lower alpha to stay the supporting trace rather than the loud one.
    trace(cell * 1.45f, 2.2f, 1.4f, 0.32f, accent, hair * 2.1f, 0.50f)
    trace(cell * 0.75f, 5.4f, -0.85f, 0f, lerp(accent, Color.White, 0.45f), hair * 1.4f, 0.30f)
}

// ----------------------------------------------------------------- sonar ----

/** A return on the scope: where it sits, and how big it paints. */
private data class Contact(val angle: Float, val range: Float, val size: Float)

private val SONAR_CONTACTS = listOf(
    Contact(0.55f, 0.78f, 0.010f),
    Contact(1.90f, 0.42f, 0.007f),
    Contact(2.85f, 0.88f, 0.013f),
    Contact(4.10f, 0.60f, 0.008f),
    Contact(5.25f, 0.33f, 0.006f),
    Contact(5.90f, 0.71f, 0.011f)
)

/**
 * A sweep, and what it finds.
 *
 * The scope itself is fixed — range rings and bearing spokes — and the only
 * moving parts are the sweep and what it lights up. Contacts brighten as the
 * line crosses them and decay behind it, which is the entire trick: without the
 * decay it is a rotating line, and with it the screen looks like it is
 * listening to something.
 *
 * The sweep is a fan of fading lines rather than a gradient wedge. A wedge
 * needs a sweep shader rebuilt every frame; twenty-six lines do not.
 */
private fun DrawScope.drawSonar(t: Float, accent: Color, dark: Boolean) {
    val turn = PI.toFloat() * 2f
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val reach = size.minDimension * 0.62f
    val hair = (size.minDimension * 0.003f).coerceAtLeast(1f)

    for (ring in 1..4) {
        drawCircle(
            color = accent.copy(alpha = 0.13f),
            radius = reach * ring / 4f,
            center = Offset(cx, cy),
            style = Stroke(width = hair)
        )
    }
    for (spoke in 0 until 8) {
        val a = spoke / 8f * turn
        drawLine(
            color = accent.copy(alpha = 0.09f),
            start = Offset(cx, cy),
            end = Offset(cx + cos(a) * reach, cy + sin(a) * reach),
            strokeWidth = hair
        )
    }

    // Wedges rather than a fan of lines. Lines share a vertex at the centre and
    // diverge with radius, so at the rim they separate into a comb — which is
    // what the first version of this looked like. Adjacent wedges share an
    // edge, so the tail stays solid all the way out.
    val head = t * turn * 2f
    val span = turn * 0.45f
    val segments = 56
    for (i in 0 until segments) {
        val f = i / segments.toFloat()
        val a0 = head - f * span
        val a1 = head - (i + 1f) / segments * span
        val fade = 1f - f
        val wedge = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + cos(a0) * reach, cy + sin(a0) * reach)
            lineTo(cx + cos(a1) * reach, cy + sin(a1) * reach)
            close()
        }
        drawPath(wedge, accent.copy(alpha = 0.17f * fade * fade))
    }

    // The leading edge, brighter than the wedge behind it. Without it the
    // sweep has no front and reads as a pie slice rather than as a line that
    // has just gone past.
    drawLine(
        color = lerp(accent, Color.White, 0.35f).copy(alpha = 0.5f),
        start = Offset(cx, cy),
        end = Offset(cx + cos(head) * reach, cy + sin(head) * reach),
        strokeWidth = hair * 1.6f
    )

    SONAR_CONTACTS.forEach { contact ->
        // How far behind the sweep this bearing is, wrapped into one turn.
        val behind = (((head - contact.angle) % turn) + turn) % turn
        val lit = (1f - behind / (turn * 0.5f)).coerceIn(0f, 1f)
        if (lit <= 0.01f) return@forEach
        val at = Offset(
            x = cx + cos(contact.angle) * reach * contact.range,
            y = cy + sin(contact.angle) * reach * contact.range
        )
        drawCircle(accent.copy(alpha = 0.18f * lit), size.minDimension * contact.size * 2.6f, at)
        drawCircle(accent.copy(alpha = 0.80f * lit * lit), size.minDimension * contact.size, at)
    }
}

// ------------------------------------------------------------- blueprint ----

/**
 * A drawing board, drawing.
 *
 * The figures trace themselves on — outline first, then the dimensions, then
 * they fade and the sheet starts again — each on its own staggered cycle, so
 * one is always being drawn while another sits finished. A plotter head runs
 * across the sheet over the top of it.
 *
 * The tracing is a dash pattern rather than a rebuilt path: one dash as long as
 * the whole outline, with the phase wound back, so the visible run grows from
 * nothing to the full perimeter. That means the perimeter has to be known, and
 * it is — a circle, a hexagon and a rectangle all have one in closed form, so
 * no path has to be measured at runtime.
 */
private fun DrawScope.drawBlueprint(t: Float, accent: Color, dark: Boolean) {
    val turn = PI.toFloat() * 2f
    val chalk = lerp(accent, if (dark) Color.White else Color(0xFF0B2545), if (dark) 0.5f else 0.3f)
    val hair = (size.minDimension * 0.003f).coerceAtLeast(1f)
    val grid = size.minDimension * 0.055f

    val slide = ((t * 2f) % 1f) * grid
    var gx = -grid + slide
    while (gx < size.width + grid) {
        drawLine(chalk.copy(alpha = 0.07f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = hair)
        gx += grid
    }
    var gy = -grid + slide
    while (gy < size.height + grid) {
        drawLine(chalk.copy(alpha = 0.07f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = hair)
        gy += grid
    }

    // How far through its own cycle a figure is: tracing, then holding, then
    // fading. The hold is the longest part — a drawing that is never simply
    // finished is a screensaver, not a drawing.
    fun stage(stagger: Float): Pair<Float, Float> {
        val cycle = ((t * 1.6f) + stagger) % 1f
        return when {
            cycle < 0.42f -> (cycle / 0.42f) to 1f
            cycle < 0.86f -> 1f to 1f
            else -> 1f to (1f - (cycle - 0.86f) / 0.14f)
        }
    }

    // The dash that makes a line appear to be drawn. Null once complete, so a
    // finished figure costs no effect at all.
    fun tracer(perimeter: Float, drawn: Float): PathEffect? =
        if (drawn >= 1f) null
        else PathEffect.dashPathEffect(floatArrayOf(perimeter, perimeter), perimeter * (1f - drawn))

    val scan = ((t * 1.2f) % 1f) * size.width * 1.3f - size.width * 0.15f

    // A bored disc, with its bolt circle.
    run {
        val (drawn, alpha) = stage(0f)
        if (alpha > 0.01f) {
            val cx = size.width * 0.30f
            val cy = size.height * 0.33f
            val r = size.minDimension * 0.16f
            val ink = chalk.copy(alpha = 0.62f * alpha)
            val effect = tracer(turn * r, drawn)

            drawCircle(ink, r, Offset(cx, cy), style = Stroke(width = hair * 1.6f, pathEffect = effect))
            drawCircle(ink, r * 0.32f, Offset(cx, cy), style = Stroke(width = hair * 1.6f, pathEffect = effect))
            for (bolt in 0 until 6) {
                val a = bolt / 6f * turn
                drawCircle(
                    color = ink,
                    radius = r * 0.09f,
                    center = Offset(cx + cos(a) * r * 0.66f, cy + sin(a) * r * 0.66f),
                    style = Stroke(width = hair, pathEffect = effect)
                )
            }
            // Centre marks last, the way a draughtsman adds them.
            if (drawn > 0.75f) {
                val late = ((drawn - 0.75f) / 0.25f).coerceIn(0f, 1f) * alpha
                val mark = chalk.copy(alpha = 0.45f * late)
                drawLine(mark, Offset(cx - r * 1.2f, cy), Offset(cx + r * 1.2f, cy), strokeWidth = hair)
                drawLine(mark, Offset(cx, cy - r * 1.2f), Offset(cx, cy + r * 1.2f), strokeWidth = hair)
            }
        }
    }

    // A hexagon, because every drawing has one thing that is not round.
    run {
        val (drawn, alpha) = stage(0.37f)
        if (alpha > 0.01f) {
            val cx = size.width * 0.76f
            val cy = size.height * 0.48f
            val r = size.minDimension * 0.12f
            val ink = chalk.copy(alpha = 0.62f * alpha)
            // A regular hexagon's side equals its circumradius, so six of them.
            val effect = tracer(r * 6f, drawn)

            val path = Path()
            for (i in 0..6) {
                val a = (i / 6f) * turn + turn / 12f
                val x = cx + cos(a) * r
                val y = cy + sin(a) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, ink, style = Stroke(width = hair * 1.6f, pathEffect = effect))
            drawCircle(ink, r * 0.42f, Offset(cx, cy), style = Stroke(width = hair * 1.6f, pathEffect = effect))
        }
    }

    // A plate, dimensioned across the bottom.
    run {
        val (drawn, alpha) = stage(0.71f)
        if (alpha > 0.01f) {
            val left = size.width * 0.22f
            val right = size.width * 0.70f
            val top = size.height * 0.70f
            val bottom = size.height * 0.83f
            val ink = chalk.copy(alpha = 0.62f * alpha)
            val effect = tracer((right - left + bottom - top) * 2f, drawn)

            drawRect(
                color = ink,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = hair * 1.6f, pathEffect = effect)
            )

            if (drawn > 0.8f) {
                val late = ((drawn - 0.8f) / 0.2f).coerceIn(0f, 1f) * alpha
                val mark = chalk.copy(alpha = 0.45f * late)
                val dim = bottom + size.minDimension * 0.05f
                drawLine(mark, Offset(left, dim), Offset(right, dim), strokeWidth = hair)
                drawLine(mark, Offset(left, bottom), Offset(left, dim + hair * 4f), strokeWidth = hair)
                drawLine(mark, Offset(right, bottom), Offset(right, dim + hair * 4f), strokeWidth = hair)
                val tick = size.minDimension * 0.014f
                drawLine(mark, Offset(left, dim), Offset(left + tick, dim - tick * 0.5f), strokeWidth = hair)
                drawLine(mark, Offset(left, dim), Offset(left + tick, dim + tick * 0.5f), strokeWidth = hair)
                drawLine(mark, Offset(right, dim), Offset(right - tick, dim - tick * 0.5f), strokeWidth = hair)
                drawLine(mark, Offset(right, dim), Offset(right - tick, dim + tick * 0.5f), strokeWidth = hair)
            }
        }
    }

    // The plotter head. A brightness gradient alone was too subtle to register
    // as movement, which was the whole complaint about this theme — a line you
    // can actually see crossing the sheet is not.
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(chalk.copy(alpha = 0f), chalk.copy(alpha = 0.10f), chalk.copy(alpha = 0f)),
            startX = scan - size.width * 0.12f,
            endX = scan + size.width * 0.12f
        )
    )
    drawLine(
        color = chalk.copy(alpha = 0.34f),
        start = Offset(scan, 0f),
        end = Offset(scan, size.height),
        strokeWidth = hair * 1.4f
    )
}

// --------------------------------------------------------------- circuit ----

/**
 * Traces, with current in them.
 *
 * Routes are fractions of the screen, so they stretch with it rather than
 * needing a layout pass. The corners are deliberately at forty-five degrees:
 * right-angled traces are the one thing board designers avoid, and getting that
 * wrong is what makes a drawn circuit look like a maze instead.
 */
private val TRACES: List<List<Offset>> = listOf(
    listOf(
        Offset(0.02f, 0.14f), Offset(0.26f, 0.14f), Offset(0.35f, 0.23f),
        Offset(0.70f, 0.23f), Offset(0.79f, 0.14f), Offset(0.98f, 0.14f)
    ),
    listOf(
        Offset(0.06f, 0.92f), Offset(0.06f, 0.62f), Offset(0.17f, 0.51f),
        Offset(0.17f, 0.36f), Offset(0.44f, 0.36f), Offset(0.53f, 0.45f),
        Offset(0.53f, 0.74f)
    ),
    listOf(
        Offset(0.97f, 0.40f), Offset(0.72f, 0.40f), Offset(0.63f, 0.49f),
        Offset(0.63f, 0.66f), Offset(0.86f, 0.66f), Offset(0.94f, 0.74f),
        Offset(0.94f, 0.96f)
    ),
    listOf(
        Offset(0.02f, 0.55f), Offset(0.28f, 0.55f), Offset(0.37f, 0.64f),
        Offset(0.37f, 0.88f), Offset(0.66f, 0.88f)
    ),
    listOf(
        Offset(0.30f, 0.02f), Offset(0.30f, 0.09f), Offset(0.41f, 0.09f),
        Offset(0.41f, 0.30f), Offset(0.88f, 0.30f), Offset(0.88f, 0.02f)
    )
)

private fun DrawScope.drawCircuit(t: Float, accent: Color, dark: Boolean) {
    val trace = lerp(accent, if (dark) Color.White else Color.Black, 0.12f)
    val spark = lerp(accent, Color.White, 0.55f)
    val stroke = (size.minDimension * 0.004f).coerceAtLeast(1.2f)

    TRACES.forEachIndexed { index, route ->
        val pts = route.map { Offset(it.x * size.width, it.y * size.height) }

        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, trace.copy(alpha = 0.26f), style = Stroke(width = stroke))

        // Pads, so a trace ends at something rather than just stopping.
        drawCircle(trace.copy(alpha = 0.34f), stroke * 2.6f, pts.first())
        drawCircle(trace.copy(alpha = 0.34f), stroke * 2.6f, pts.last())

        // One pulse per trace, each on its own period so they never march.
        val runs = pts.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
        val total = runs.sum()
        if (total <= 0f) return@forEachIndexed

        val travelled = ((t * (0.8f + index * 0.17f)) % 1f) * total
        var walked = 0f
        for (i in runs.indices) {
            if (travelled <= walked + runs[i]) {
                val f = if (runs[i] == 0f) 0f else (travelled - walked) / runs[i]
                val at = Offset(
                    x = pts[i].x + (pts[i + 1].x - pts[i].x) * f,
                    y = pts[i].y + (pts[i + 1].y - pts[i].y) * f
                )
                drawCircle(accent.copy(alpha = 0.20f), stroke * 5f, at)
                drawCircle(spark.copy(alpha = 0.85f), stroke * 1.7f, at)
                break
            }
            walked += runs[i]
        }
    }
}

// ------------------------------------------------------------------ camo ----

/**
 * Camouflage, drifting.
 *
 * Three tones in layers, because real camouflage is layered and one blob colour
 * on a ground is a lava lamp. The blotches barely move — fast camouflage is
 * fluid, and this should look like a pattern printed on something that shifts.
 */
private fun DrawScope.drawCamo(field: List<Mote>, t: Float, accent: Color) {
    val turn = PI.toFloat() * 2f
    val tones = listOf(
        accent.copy(alpha = 0.15f),
        lerp(accent, Color.Black, 0.5f).copy(alpha = 0.20f),
        lerp(accent, Color(0xFFC9BF8C), 0.55f).copy(alpha = 0.11f)
    )

    field.forEachIndexed { index, blob ->
        val drift = (t * blob.speed * 0.12f + blob.phase) % 1f
        val cx = size.width * blob.x + sin((drift + blob.phase) * turn) * size.width * blob.sway
        val cy = size.height * ((blob.phase * 1.13f + drift * 0.3f) % 1f)
        val r = size.minDimension * (0.07f + blob.size * 2.2f)
        drawPath(camoPath(cx, cy, r, index), tones[index % tones.size])
    }
}

/**
 * One blotch.
 *
 * Three harmonics out of phase with each other, which gives the lumpy, cornered
 * outline camouflage has. An ellipse reads as a pebble and a two-harmonic
 * wobble reads as a cloud; it takes the third one to look cut out.
 */
private fun camoPath(cx: Float, cy: Float, r: Float, seed: Int): Path = Path().apply {
    val turn = PI.toFloat() * 2f
    val steps = 26
    for (i in 0..steps) {
        val a = i / steps.toFloat() * turn
        val wobble = 1f +
            0.34f * sin(a * 3f + seed * 1.7f) +
            0.22f * sin(a * 5f - seed * 2.3f) +
            0.12f * sin(a * 8f + seed * 0.9f)
        val x = cx + cos(a) * r * wobble
        val y = cy + sin(a) * r * wobble * 0.78f
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
