package com.mentora.android.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit test (`testDebugUnitTest`, no Robolectric/instrumentation needed — [ImageVector]/
 * [androidx.compose.ui.graphics.vector.PathParser]/[androidx.compose.ui.graphics.Color] are pure
 * Kotlin, part of Compose's multiplatform graphics/vector layer, same reasoning
 * `MentoraTokensDriftTest` already relies on for plain `Color` construction). Verifies every one of
 * [MentoraIconName]'s 42 entries constructs a real [androidx.compose.ui.graphics.vector.ImageVector]
 * with **at least one real path node under its root group** (every icon in `MentoraIcons.kt` calls
 * `addPath` directly on the builder via `strokePath`/`filledPath`, so `vector.root` having a non-zero
 * child count *is* "at least one path" here, not a looser proxy for it), without
 * [MentoraIcons.forName] throwing — the real risk this catches is a malformed SVG-style path-data
 * string (a bad arc/curve command) that [androidx.compose.ui.graphics.vector.PathParser] would reject
 * at construction time, something a successful `compileDebugKotlin` alone can't catch since path data
 * is just a `String` literal.
 */
class MentoraIconsTest {

    @Test
    fun `every icon name resolves to a non-empty ImageVector with at least one path`() {
        val names = MentoraIconName.entries
        assertTrue("expected 42 ported icons, found ${names.size}", names.size == 42)

        for (name in names) {
            // Constructing the ImageVector is the real assertion: PathParser (invoked lazily inside
            // MentoraIcons.forName -> buildIcon -> strokePath/filledPath) throws on malformed
            // path-data, which is exactly the class of bug this test exists to catch.
            val vector = MentoraIcons.forName(name)
            assertTrue("icon $name has an empty name", vector.name.isNotEmpty())
            // F9: actually assert at least one path node exists under the root group, rather than
            // just asserting the vector's own name string is non-empty (which says nothing about
            // whether any path was actually added).
            assertTrue("icon $name has no path nodes under its root group", vector.root.size > 0)
        }
    }
}
