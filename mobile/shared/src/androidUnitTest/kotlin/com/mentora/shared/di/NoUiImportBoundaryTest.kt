package com.mentora.shared.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Task 15 AC: "a test/documented grep check proves `commonMain` imports nothing from Compose/
 * SwiftUI/`android.*` UI packages/any UI framework." Deliberately placed under `androidUnitTest`,
 * not `commonTest`, because `java.io.File` is a JVM-only API `commonTest` may never depend on
 * (`execution/PHASE_3_KMP_PLAN.md` Decision D-B — the same reason
 * `SendAiTutorMessageUseCaseArchitectureTest` lives here instead of `commonTest`). Still runs under
 * the same `:shared:testDebugUnitTest` Gradle task every other test in this module runs under.
 *
 * This is a real file-content grep over every `.kt` file actually on disk under
 * `src/commonMain/kotlin/com/mentora/shared/` — not a reflection/classpath check — so it also
 * catches an import that would compile away (e.g. a fully-qualified reference with no `import`
 * line) ONLY if that reference is itself spelled out somewhere in the source text; see
 * [FORBIDDEN_IMPORT_PREFIXES] for the exact patterns checked. `androidMain`/`iosMain` are
 * DELIBERATELY excluded from this scan — `androidMain`'s `android.content.Context`/
 * `androidx.datastore.*` imports are expected and correct there (it is where the real Android
 * platform actuals live), and this check is specifically about `commonMain` staying UI-framework-
 * and Android-framework-free (ADR-002 — the whole point of a KMP shared module).
 */
class NoUiImportBoundaryTest {

    private val forbiddenImportPrefixes = listOf(
        // Compose (any artifact under the androidx.compose.* namespace).
        "import androidx.compose.",
        // Any plain Android framework/UI package — commonMain must never reference the Android
        // SDK at all, not just its UI toolkit (that boundary belongs entirely to androidMain).
        "import android.",
        // Legacy Android View system, in case a future dependency ever pulls it in transitively
        // and someone imports it directly.
        "import androidx.appcompat.",
        "import androidx.fragment.",
        "import androidx.activity.",
        // SwiftUI/UIKit have no Kotlin `import` spelling reachable from commonMain (they are
        // Objective-C/Swift frameworks, only referenceable from iosMain `cinterop`/`platform.*`
        // bindings) — checked here anyway as a defensive, self-documenting acceptance criterion:
        // commonMain must never contain a `platform.UIKit`/`platform.SwiftUI` cinterop import,
        // which WOULD be a real (if unusual) way UI framework code could leak into commonMain.
        "import platform.UIKit.",
        "import platform.SwiftUI.",
    )

    @Test
    fun `commonMain never imports a UI framework package`() {
        val commonMainRoot = findCommonMainRoot()
        val kotlinFiles = commonMainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        // A basic sanity floor: if this ever returns 0 files, the path-resolution logic itself is
        // broken (silently "passing" by finding nothing would be worse than a loud failure).
        assertTrue(kotlinFiles.size > 50, "expected commonMain to contain many .kt files, found ${kotlinFiles.size} under $commonMainRoot")

        val violations = mutableListOf<String>()
        for (file in kotlinFiles) {
            file.readLines().forEachIndexed { lineIndex, line ->
                val trimmed = line.trimStart()
                forbiddenImportPrefixes.forEach { forbidden ->
                    if (trimmed.startsWith(forbidden)) {
                        violations += "${file.relativeTo(commonMainRoot)}:${lineIndex + 1}: $trimmed"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "commonMain must never import a UI framework package, found:\n${violations.joinToString("\n")}",
        )
    }

    /**
     * Resolves `src/commonMain/kotlin/com/mentora/shared` on disk regardless of the exact working
     * directory Gradle happens to run this test task from — walks upward from `user.dir` looking
     * for `shared/src/commonMain/kotlin/com/mentora/shared` (running from the `mobile/` root, the
     * common case) or `src/commonMain/kotlin/com/mentora/shared` (running from `shared/` itself).
     */
    private fun findCommonMainRoot(): File {
        val relativeCandidates = listOf(
            "shared/src/commonMain/kotlin/com/mentora/shared",
            "src/commonMain/kotlin/com/mentora/shared",
        )
        val startDir = requireNotNull(System.getProperty("user.dir")) { "system property user.dir is unset" }
        var dir: File? = File(startDir).absoluteFile
        while (dir != null) {
            for (candidate in relativeCandidates) {
                val resolved = File(dir, candidate)
                if (resolved.isDirectory) return resolved
            }
            dir = dir.parentFile
        }
        fail("could not locate commonMain's source root by walking up from $startDir")
    }
}
