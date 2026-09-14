package com.mentora.android.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.mentora.shared.settings.AppLocale
import java.util.Locale

/**
 * T18 — makes Settings' Language selector functionally real. Before this task, `sdk.user.setLocale`
 * only ever did two things: persisted a preference (read back for the `?language=` query param
 * `shared` already threads through every request) and drove [com.mentora.android.theme.MentoraTheme]
 * `arabicScript` param's typography adjustment (letter-spacing/line-height) — it never touched what
 * `stringResource(...)` calls anywhere in the app actually resolve to, which stays governed entirely
 * by the DEVICE's own OS-level locale (`AiTutorViewModel.kt`'s own `quickActionPrompt` kdoc already
 * flagged this exact gap by name: "this app never calls `setApplicationLocales`/
 * `createConfigurationContext`... if per-app locale override is ever wired up, this specific pairing
 * would need revisiting"). `ux/SCREEN_UX_SPECS.md § 17`'s Language field is a LOCKED MVP requirement,
 * not a deferrable gap like the Arabic font asset (Task 2).
 *
 * **Why this shape, not `AppCompatDelegate.setApplicationLocales`** (the standard AndroidX per-app-
 * language mechanism): that API's own documented auto-persist/auto-recreate path requires the
 * launching `Activity` to extend `AppCompatActivity` (or hand-roll the identical `attachBaseContext`
 * wiring itself) to work correctly below API 33 — this app's `minSdk` is 26, and `MainActivity`
 * deliberately extends the plain `ComponentActivity` (no AppCompat theme/dependency exists anywhere
 * else in this codebase; adding one for this alone is a disproportionate footprint change). This
 * wrapper instead uses the pure-Compose technique: override the ambients [stringResource]/every other
 * resource lookup actually read — no `Activity` recreate, no minSdk-dependent code path, and it
 * satisfies the spec's own "no navigation away... no app restart" requirement more directly than a
 * recreate-based approach would (screen/ViewModel state never needs saving-and-restoring across a
 * configuration change at all, because there never is one).
 *
 * **[LocalizedResourcesContextWrapper], not a raw `Context.createConfigurationContext(...)` result,
 * is what gets provided as [LocalContext] — this is NOT an interchangeable implementation detail.**
 * `createConfigurationContext` returns a brand-new `ContextImpl` rooted at the Application, not a
 * [ContextWrapper] around the original base [Context] — providing that raw result as [LocalContext]
 * silently severs the `ContextWrapper.baseContext` chain every "walk up from `LocalContext.current` to
 * find the host `Activity`" helper (window-insets controllers, several Compose-ecosystem utilities,
 * this app's own future needs) depends on, and breaks `Context.startActivity` from inside the wrapped
 * subtree. [LocalizedResourcesContextWrapper] instead wraps the REAL base [Context] and overrides only
 * [Context.getResources]/[Context.getAssets] — [ContextWrapper]'s default delegation keeps every other
 * call (`startActivity`, the Activity-lookup walk) working exactly as before. Empirically verified
 * against both shapes (an instrumented spike, since discarded once its findings were folded in here):
 * the [ContextWrapper] shape keeps Activity lookup/`startActivity` intact and still correctly flips
 * `stringResource`/[LocalConfiguration]/layout direction; the raw `createConfigurationContext` shape
 * broke Activity lookup and made `startActivity` throw, from the very same wrapped subtree.
 *
 * **[LocalConfiguration] is ALSO overridden, not just [LocalContext]** — [com.mentora.android.ui
 * .certificates.CertificateDetailScreen]/`CertificatesScreen` both read `LocalConfiguration.current
 * .locales.get(0)` directly for date formatting, a real existing call site that would otherwise keep
 * reading the device's OS locale even after this wrapper made every OTHER string in the app switch —
 * a visible post-switch inconsistency this task's own reviewer would certainly have caught. Both
 * ambients are backed by the SAME mutated [Configuration] object, so they can never drift from each
 * other. [LocalLayoutDirection] is overridden alongside them for the spec's third requirement
 * ("flips layout direction... app-wide") — Compose does not derive layout direction from
 * [LocalConfiguration] on its own; it must be set explicitly.
 *
 * **[LocalAppLocale] + [WithCurrentAppLocale] — the Dialog/Popup/BottomSheet gap.** The same spike
 * proved `Dialog`/`Popup` (and therefore `ModalBottomSheet`, built on `Popup`) reset [LocalContext]/
 * [LocalConfiguration] back to their OWN window's system-default values for their sub-composition —
 * NOT inherited from whatever this wrapper provided further up the tree — while [LocalLayoutDirection]
 * and any ordinary custom [androidx.compose.runtime.CompositionLocal] (like [LocalAppLocale] itself)
 * DO still cross that boundary untouched. A `stringResource` call made directly inside a `Dialog`/
 * `Popup`/`ModalBottomSheet`'s own content would therefore silently fall back to the device's OS
 * locale even after a language switch. [com.mentora.android.ui.components.AppDialog] never calls
 * `stringResource` inside its own `Dialog{}` body (every string it renders arrives as an already-
 * resolved `String` parameter from its caller, composed OUTSIDE the dialog boundary) so it needs no
 * fix. [com.mentora.android.ui.components.MentoraBottomSheet] is the one real, live exception — its
 * `content` lambda IS invoked inside `ModalBottomSheet`'s own sub-composition, and real call sites
 * (`CurriculumBottomSheet`, Task 13) do call `stringResource` from inside it — [WithCurrentAppLocale]
 * is wrapped around that one call site to close the gap. A grep across this whole module at the time
 * this was written confirmed `AppDialog.kt`/`MentoraBottomSheet.kt` are the only two `Dialog`/`Popup`/
 * `ModalBottomSheet` consumers that exist — no other call site is currently affected.
 */
val LocalAppLocale = staticCompositionLocalOf { AppLocale.English }

private class LocalizedResourcesContextWrapper(base: Context, private val localizedResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
    override fun getAssets(): AssetManager = localizedResources.assets
}

private fun Context.localizedFor(locale: AppLocale): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(Locale.forLanguageTag(locale.wireValue)))
    return LocalizedResourcesContextWrapper(this, createConfigurationContext(configuration).resources)
}

@Composable
fun LocalizedContent(locale: AppLocale, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val localizedContext = remember(locale, baseContext) { baseContext.localizedFor(locale) }
    val layoutDirection = when (locale) {
        AppLocale.Arabic -> LayoutDirection.Rtl
        AppLocale.English -> LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalAppLocale provides locale,
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides layoutDirection,
        content = content,
    )
}

/** Call from inside a `Dialog`/`Popup`/`ModalBottomSheet`'s own content — see this file's own kdoc,
 *  "The Dialog/Popup/BottomSheet gap," for why this is needed there and nowhere else a plain
 *  [LocalizedContent] ambient is already in scope. */
@Composable
fun WithCurrentAppLocale(content: @Composable () -> Unit) {
    LocalizedContent(locale = LocalAppLocale.current, content = content)
}
