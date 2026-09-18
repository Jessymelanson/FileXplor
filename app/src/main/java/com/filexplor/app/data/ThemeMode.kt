package com.filexplor.app.data

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * The moving layer an animated palette draws behind the page.
 *
 * Named for what it looks like rather than how it is drawn, because the choice
 * belongs to the user and appears in the theme picker by that name.
 */
enum class ThemeMotion {
    FIREWORKS,
    RAIN,
    LAVA,
    BUBBLES,
    CONSTELLATIONS,
    SYNTHWAVE,
    CONTOURS,
    TERMINAL,
    ORBITS,
    PETALS,
    HEARTS,
    SNOW,
    LEAVES,
    AURORA,
    BATS,
    LIGHTS,
    ENGINE,
    THUNDER,
    GEARS,
    OSCILLOSCOPE,
    SONAR,
    BLUEPRINT,
    CIRCUIT,
    CAMO
}

/**
 * Colour scheme, chosen independently of light/dark. Every palette defines both
 * a light and a dark form, so switching mode never strands the user in an
 * unreadable combination.
 *
 * The last five also carry a [ThemeMotion]. Those are ordinary palettes in
 * every other respect — they have the same light and dark forms as the rest,
 * and the animation is an extra layer rather than a different kind of theme.
 * A palette with no motion costs exactly what it did before: nothing is drawn
 * and no animation runs.
 */
enum class ThemePalette(val label: String, val motion: ThemeMotion? = null) {
    PAPER("Paper"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    VIOLET("Violet"),
    NORD("Nord"),
    SOLARIZED("Solarized"),
    MONO("Mono"),
    SPARKS("Fireworks", ThemeMotion.FIREWORKS),
    STORM("Rainfall", ThemeMotion.RAIN),
    LAVA("Lava lamp", ThemeMotion.LAVA),
    REEF("Bubbles", ThemeMotion.BUBBLES),
    COSMOS("Star map", ThemeMotion.CONSTELLATIONS),
    RETRO("Synthwave", ThemeMotion.SYNTHWAVE),
    RELIEF("Contours", ThemeMotion.CONTOURS),
    PHOSPHOR("Terminal", ThemeMotion.TERMINAL),
    ORRERY("Orbits", ThemeMotion.ORBITS),
    BLOSSOMFALL("Cherry blossom", ThemeMotion.PETALS),
    BLOSSOM("Pink hearts", ThemeMotion.HEARTS),
    FROST("Snowfall", ThemeMotion.SNOW),
    AUTUMN("Falling leaves", ThemeMotion.LEAVES),
    BOREALIS("Aurora", ThemeMotion.AURORA),
    HALLOWEEN("Halloween", ThemeMotion.BATS),
    YULE("Christmas lights", ThemeMotion.LIGHTS),
    PISTON("Engine", ThemeMotion.ENGINE),
    TEMPEST("Thunderstorm", ThemeMotion.THUNDER),
    MACHINE("Gears", ThemeMotion.GEARS),
    SCOPE("Oscilloscope", ThemeMotion.OSCILLOSCOPE),
    DEEP("Sonar", ThemeMotion.SONAR),
    DRAFT("Blueprint", ThemeMotion.BLUEPRINT),
    BOARD("Circuit board", ThemeMotion.CIRCUIT),
    RECON("Camo", ThemeMotion.CAMO)
}

