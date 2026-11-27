package io.github.and.sandtimer.data

/**
 * How the glass can look, as the Mac's Appearance menu offers it (Sources/HourglassRenderer.swift, `Theme`): a sand
 * colour on a base — the caps — black like the purple product photo, or matching like the teal one.
 */

/** The caps under and over the glass. */
enum class Base {
    BLACK, MATCHING;

    companion object {
        /** The one the settings keep, by name; black for anything else. */
        fun named(name: String?) = entries.firstOrNull { it.name == name } ?: BLACK
    }
}

class SandColor(val sand: String, val matchingCap: String) {
    companion object {
        /** The Mac's colours, in its order (`Theme.colors`): the sand, and the cap that matches it, sRGB as "#RRGGBB". */
        val all = listOf(
            SandColor("#6C2ED6", "#966CE4"),  // Purple
            SandColor("#34BEA6", "#62CEBA"),  // Teal
            SandColor("#EC62A0", "#F496C0"),  // Pink
            SandColor("#2876E2", "#6CA2EE"),  // Blue
            SandColor("#ECE9E2", "#EEEEEC"),  // White
            SandColor("#222124", "#2C2C30"),  // Black
            SandColor("#D6B688", "#E4CEAC"),  // Sand, the colour of real sand
        )
    }
}
