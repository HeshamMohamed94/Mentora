package com.mentora.android.locale

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * T18 — a plain JVM key-parity guard between `values/strings.xml` and `values-ar/strings.xml`,
 * parsing both files directly at test time (mirrors `MentoraTokensDriftTest`'s "parse the real
 * source, don't trust a generator's self-report" pattern; no Android resource merging exists in a
 * plain JVM test).
 *
 * **Why this test is more load-bearing after this task than before it.** Before `LocalizedContent`
 * existed, a missing `values-ar` key was invisible on any device actually running in Arabic — the
 * whole APP was still governed by the device's own OS locale, so an Arabic device simply never
 * rendered that string at all in practice (every screen's real content was English regardless of
 * this file's own completeness). Now that `sdk.user.setLocale(AppLocale.Arabic)` genuinely flips
 * `stringResource` app-wide (this task's own central mechanism, see `LocalizedContent.kt`'s kdoc), a
 * device set to English whose STUDENT switches the in-app language to Arabic will reach every one of
 * these keys for real — a gap here now silently renders one leftover English string inside an
 * otherwise fully-Arabic screen, not merely a translation debt with no live reachability.
 */
class StringsParityTest {

    private val repoRoot: File = findRepoRoot()

    private val englishKeys: Set<String> by lazy { stringKeysIn(File(repoRoot, "mobile/androidApp/src/main/res/values/strings.xml")) }
    private val arabicKeys: Set<String> by lazy { stringKeysIn(File(repoRoot, "mobile/androidApp/src/main/res/values-ar/strings.xml")) }

    /** `app_name` is the one sanctioned exception — the launcher label correctly follows the OS
     *  locale (a system-level convention, not this app's own [com.mentora.shared.settings.AppLocale]),
     *  so `values-ar` deliberately carries no override for it. */
    private val sanctionedEnglishOnlyKeys = setOf("app_name")

    @Test
    fun everyEnglishKey_hasAnArabicTranslation() {
        val missing = (englishKeys - sanctionedEnglishOnlyKeys) - arabicKeys
        assertTrue("values-ar/strings.xml is missing translations for: $missing", missing.isEmpty())
    }

    @Test
    fun arabicHasNoOrphanKeys_beyondWhatEnglishDefines() {
        val orphaned = arabicKeys - englishKeys
        assertTrue("values-ar/strings.xml defines keys with no values/strings.xml counterpart: $orphaned", orphaned.isEmpty())
    }

    @Test
    fun neitherFileHasAnEmptyOrBlankValue() {
        assertEmptyValues(File(repoRoot, "mobile/androidApp/src/main/res/values/strings.xml"))
        assertEmptyValues(File(repoRoot, "mobile/androidApp/src/main/res/values-ar/strings.xml"))
    }

    /** Review fix (LOW): key parity alone doesn't catch a translation that DROPS or RETYPES a
     *  positional format argument — `String.format`/`getString(id, arg)` throws
     *  `MissingFormatArgumentException`/`IllegalFormatConversionException` at runtime for exactly
     *  that mismatch, and it would only surface the first time a Student actually reached that
     *  screen in Arabic (now genuinely reachable on an English device, per this class's own kdoc) —
     *  never at build time otherwise. Compares the SET of positional specifiers (`%1$s`, `%2$d`, ...)
     *  per key; specifier ORDER may legitimately differ between languages (grammar reordering), but
     *  which positions/types exist must not. */
    @Test
    fun everyTranslatedString_keepsTheSameFormatSpecifiersAsItsEnglishSource() {
        val englishValues = stringValuesIn(File(repoRoot, "mobile/androidApp/src/main/res/values/strings.xml"))
        val arabicValues = stringValuesIn(File(repoRoot, "mobile/androidApp/src/main/res/values-ar/strings.xml"))
        val mismatches = mutableListOf<String>()

        for ((name, englishText) in englishValues) {
            val arabicText = arabicValues[name] ?: continue // Missing-key case — covered by its own test above.
            val englishSpecifiers = formatSpecifiersIn(englishText)
            val arabicSpecifiers = formatSpecifiersIn(arabicText)
            if (englishSpecifiers != arabicSpecifiers) {
                mismatches += "$name: en=$englishSpecifiers ar=$arabicSpecifiers"
            }
        }

        assertTrue("Format-specifier mismatches between values/ and values-ar/: $mismatches", mismatches.isEmpty())
    }

    private val formatSpecifierPattern = Regex("""%(\d+)\$([sd])""")

    private fun formatSpecifiersIn(text: String): Set<String> =
        formatSpecifierPattern.findAll(text).map { it.value }.toSet()

    private fun assertEmptyValues(file: File) {
        val blanks = mutableListOf<String>()
        forEachStringElement(file) { name, text -> if (text.isBlank()) blanks += name }
        assertTrue("${file.name} has blank <string> values: $blanks", blanks.isEmpty())
    }

    private fun stringKeysIn(file: File): Set<String> = stringValuesIn(file).keys

    private fun stringValuesIn(file: File): Map<String, String> {
        val values = mutableMapOf<String, String>()
        forEachStringElement(file) { name, text -> values[name] = text }
        return values
    }

    private fun forEachStringElement(file: File, action: (name: String, text: String) -> Unit) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i)
            val name = element.attributes.getNamedItem("name")?.nodeValue ?: continue
            action(name, element.textContent ?: "")
        }
    }

    private fun findRepoRoot(): File {
        val startDir = requireNotNull(System.getProperty("user.dir")) { "system property user.dir is unset" }
        var dir: File? = File(startDir).absoluteFile
        while (dir != null) {
            if (File(dir, "design-system/design-tokens.json").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (design-system/design-tokens.json) by walking up from $startDir")
    }
}
