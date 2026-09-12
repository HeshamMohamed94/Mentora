package com.mentora.shared.data.network

import com.mentora.shared.settings.AppLocale

/**
 * The one shared mechanism for threading the active UI locale onto a `?language=` query parameter
 * on a read endpoint. Used by `CatalogRepositoryImpl` (course list/detail, Task 7) and, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 7 AC #5, intended for reuse by Task 8's checkout-preview
 * read and Task 12's learning-path-detail read — every "append the active locale as a query
 * param" call site should go through this one function rather than re-deriving the wire key
 * (`"language"`) and the [AppLocale] -> wire-value mapping per call site.
 *
 * [AppLocale] (the UI's display language) is never conflated with a course's `contentLanguage`
 * (`com.mentora.shared.domain.model.ContentLanguage`) even though today they share the same two
 * wire values ("en"/"ar") — this function's input type is deliberately [AppLocale] only, and there
 * is no overload accepting a raw `String` or "either kind of language."
 */
fun localeQueryParam(locale: AppLocale): Pair<String, String> = "language" to locale.wireValue
