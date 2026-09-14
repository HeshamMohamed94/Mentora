package com.mentora.android.viewmodel

import com.mentora.shared.settings.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * T19 — the one shared mechanism every locale-sensitive ViewModel in this phase uses to reload its
 * own server-sourced primary content when the active UI locale changes while that ViewModel
 * instance is still alive. `MentoraNavHost`'s `popUpTo { saveState = true }` tab-switch mechanism
 * keeps a tab-root ViewModel alive (merely backgrounded, not destroyed) across a tab switch — so a
 * locale change made from Settings while, say, Explore's ViewModel is backgrounded would otherwise
 * leave stale-locale content rendering until that tab is fully left and re-entered (or the process
 * restarts). See `execution/DECISIONS_LOG.md` D93 (where this gap was found and deliberately
 * deferred here) and D94 (this task's own entry) for the full account.
 *
 * [observeLocale] is a lambda-constructor seam, the same convention every ViewModel in this phase
 * already uses — production wires it to `sdk.user.observeLocale`; a JVM test wires a plain
 * `MutableStateFlow<AppLocale>` instead, letting a test drive a locale change deterministically with
 * no Android/`shared`-internal dependency.
 *
 * `.drop(1)` is essential, not an optimization: the flow's value at collection time is whatever
 * locale was ALREADY active when this same `init` block's own initial load already fired with that
 * locale — without the drop, every consumer would double-fetch its primary content on every cold
 * start.
 *
 * Deliberately NOT applied to every ViewModel in this phase — see each Factory's own kdoc for the
 * ones that opt out and why (in short: a screen with in-flight, user-entered state a forced reload
 * could destroy — `CoursePlayerViewModel`'s actively-tracked playback/completion state,
 * `QuizViewModel`'s in-progress answers, `CheckoutViewModel`/`PurchaseSuccessViewModel`'s one-shot
 * transactional flow — is excluded on purpose, not an oversight).
 */
fun CoroutineScope.reloadOnLocaleChange(observeLocale: () -> StateFlow<AppLocale>, onLocaleChanged: () -> Unit) {
    launch {
        observeLocale().drop(1).collect { onLocaleChanged() }
    }
}
