package com.mentora.android.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.R
import com.mentora.shared.settings.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/**
 * T18 — real regression coverage for `LocalizedContent.kt`'s production mechanism (the one
 * [com.mentora.android.MainActivity]/[com.mentora.android.ui.components.MentoraBottomSheet] actually
 * use), distilled from an exploratory instrumented spike that first proved this shape out (and was
 * discarded once its findings were folded into that file's own kdoc). Covers the two properties that
 * matter and are NOT obvious from reading the code alone: (1) switching [LocalizedContent]'s `locale`
 * genuinely re-resolves `stringResource`/[LocalConfiguration]/[LocalLayoutDirection] with no Activity
 * recreation, and (2) the [LocalizedResourcesContextWrapper] shape (not a raw
 * `createConfigurationContext` result) is what keeps `Context`-walking Activity lookup working from
 * inside the wrapped subtree — the specific, easy-to-regress-into mistake this file's own kdoc warns
 * against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class LocalizedContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingLocale_flipsStringsConfigurationAndLayoutDirection_withNoActivityRecreation() {
        var locale by mutableStateOf(AppLocale.English)
        var observedDirection: LayoutDirection? = null
        var observedConfigLanguage: String? = null
        val activityBefore = composeRule.activity

        composeRule.setContent {
            LocalizedContent(locale) {
                observedDirection = LocalLayoutDirection.current
                observedConfigLanguage = LocalConfiguration.current.locales.get(0)?.language
                Text(stringResource(R.string.nav_home))
            }
        }

        composeRule.onNodeWithText("Home").assertExists()
        assertEquals(LayoutDirection.Ltr, observedDirection)
        assertEquals("en", observedConfigLanguage)

        composeRule.runOnUiThread { locale = AppLocale.Arabic }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ArabicHome).assertExists()
        assertEquals(LayoutDirection.Rtl, observedDirection)
        assertEquals("ar", observedConfigLanguage)
        assertSame("switching locale must never recreate the Activity", activityBefore, composeRule.activity)
    }

    @Test
    fun withinLocalizedContent_activityLookupAndStartActivityStillWork() {
        var foundActivity: Activity? = null

        composeRule.setContent {
            LocalizedContent(AppLocale.Arabic) {
                foundActivity = LocalContext.current.findActivity()
                Text(stringResource(R.string.nav_home))
            }
        }
        composeRule.waitForIdle()

        // The regression this test exists for: a raw `createConfigurationContext(...)` result
        // provided as LocalContext (the bug this file's own kdoc documents having caught) breaks this
        // exact lookup — see that kdoc's "empirically verified against both shapes" note.
        assertNotNull("Activity lookup must survive the localized Context wrapper", foundActivity)
        assertSame(composeRule.activity, foundActivity)
    }

    @Test
    fun withCurrentAppLocale_insideADialog_restoresTheChosenLocale() {
        var dialogText: String? = null
        var dialogDirection: LayoutDirection? = null

        composeRule.setContent {
            LocalizedContent(AppLocale.Arabic) {
                Dialog(onDismissRequest = {}) {
                    WithCurrentAppLocale {
                        dialogDirection = LocalLayoutDirection.current
                        dialogText = stringResource(R.string.nav_explore)
                        Text("dialog-probe")
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("dialog-probe").assertExists()

        assertEquals(ArabicExplore, dialogText)
        assertEquals(LayoutDirection.Rtl, dialogDirection)
    }

    @Test
    fun withCurrentAppLocale_insideAModalBottomSheet_restoresTheChosenLocale() {
        var sheetText: String? = null

        composeRule.setContent {
            LocalizedContent(AppLocale.Arabic) {
                ModalBottomSheet(onDismissRequest = {}) {
                    WithCurrentAppLocale {
                        sheetText = stringResource(R.string.nav_explore)
                        Text("sheet-probe")
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("sheet-probe").assertExists()

        assertEquals(ArabicExplore, sheetText)
    }
}

private const val ArabicHome = "الرئيسية"
private const val ArabicExplore = "استكشف"
