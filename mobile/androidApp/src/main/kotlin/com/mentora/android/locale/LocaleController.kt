package com.mentora.android.locale

import android.content.Context
import androidx.core.os.LocaleListCompat
import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.AuthState
import com.mentora.shared.settings.resolveInitialLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BOOTSTRAP_PREFS_FILE = "mentora_locale_bootstrap"
private const val KEY_HAS_SEEDED_LOCALE = "has_seeded_locale"

/**
 * T4 Part C — first-run locale seeding (G2 resolution, `PHASE_4_ANDROID_PLAN.md` § 6).
 *
 * `AndroidPreferenceStore` defaults its persisted locale to `"en"` with no way to distinguish "the
 * user actually chose English" from "nothing has ever been written yet" (its backing
 * `multiplatform-settings` read is a plain `getString(key, default)` — see that class's source).
 * `resolveInitialLocale` (a pure `commonMain` function `shared` never calls itself, by design) must
 * therefore be invoked here, exactly once ever per install, behind this class's OWN app-level flag —
 * a plain boolean in a dedicated `SharedPreferences` file, deliberately separate from
 * `AndroidPreferenceStore`'s own backing store, so this bootstrap logic never has to guess at
 * `shared`'s internal storage shape to tell "never asked" apart from "already chose English."
 *
 * [LocaleListCompat.getAdjustedDefault] (not the raw `Locale.getDefault()`) is used to build the
 * system-locale tag list: it returns the user's actual ranked system-locale preference list
 * (Android 13+ per-app language / the device's Settings > Languages order on older versions),
 * which is the real signal `resolveInitialLocale`'s kdoc asks a platform to supply — a single
 * `Locale.getDefault()` call would only ever see the single top-ranked locale, silently discarding
 * a user's 2nd/3rd-ranked Arabic preference if English happened to rank first.
 */
class LocaleController(private val context: Context, private val sdk: MentoraSdk) {

    /** No-op if this install has already seeded its initial locale once, or if a session is
     * already known/authenticated (see F2 fix below) — safe to call on every app cold start.
     *
     * **F3 fix**: this is only ever invoked once, from `MentoraApplication`'s single
     * application-scoped bootstrap coroutine, strictly AFTER `sdk.auth.restoreSession()` has
     * already completed. Auth state is therefore guaranteed terminal (never [AuthState.Unknown])
     * by the time the check below runs — no other call site anywhere in the app calls
     * `restoreSession()` or this method, so there is no race to guard against here.
     *
     * Runs entirely on [Dispatchers.IO]: both the `SharedPreferences` file access (deferred until
     * here rather than done eagerly at construction time) and the flag write are disk I/O. */
    suspend fun seedInitialLocaleIfNeeded() = withContext(Dispatchers.IO) {
        val bootstrapPrefs = context.applicationContext.getSharedPreferences(BOOTSTRAP_PREFS_FILE, Context.MODE_PRIVATE)
        if (bootstrapPrefs.getBoolean(KEY_HAS_SEEDED_LOCALE, false)) return@withContext

        // F2 fix: only seed a genuinely fresh, never-logged-in install. If a session is already
        // known/authenticated by the time this runs (e.g. the seed flag was lost to an async
        // `apply()` before a process death, and the user has since logged in and deliberately
        // picked a language), skip seeding AND leave the flag unset — calling `setLocale` here
        // would both clobber the user's local choice and PATCH their real server-side
        // `preferredLocale`, propagating the wrong locale to their other devices.
        if (sdk.auth.observeAuthState().value is AuthState.Authenticated) return@withContext

        val systemTags = LocaleListCompat.getAdjustedDefault()
            .toLanguageTags()
            .split(',')
            .filter { it.isNotBlank() }
        val resolved = resolveInitialLocale(systemTags)
        sdk.user.setLocale(resolved)

        // F2 fix: this flag is written exactly once per install, ever — the synchronous cost of
        // `commit()` is irrelevant, and unlike `apply()` it can't be lost to process death before
        // the next flush point (e.g. Settings "Force stop" on first launch), which would otherwise
        // cause this seed to silently re-run on a later cold start.
        bootstrapPrefs.edit().putBoolean(KEY_HAS_SEEDED_LOCALE, true).commit()
    }
}
