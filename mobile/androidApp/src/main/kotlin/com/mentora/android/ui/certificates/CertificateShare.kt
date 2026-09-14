package com.mentora.android.ui.certificates

import android.content.Context
import androidx.core.app.ShareCompat

/**
 * `product/PRODUCT_SPEC.md § 13`: "no real LinkedIn API integration in MVP" — confirmed via the
 * backend that no public/unauthenticated "verify a certificate" endpoint exists anywhere, so there is
 * nothing real to generate a shareable link to (same disclosed scope as Task 11's Demo Checkout: a
 * real platform mechanism — here, the OS share sheet; there, a fake payment form — wired to plain
 * text, never a fabricated backend integration). [ShareCompat.IntentBuilder] launches the REAL Android
 * share sheet (`Intent.ACTION_SEND`, `text/plain`) so a user can genuinely share to whatever app they
 * choose (Messages, real LinkedIn, email, ...) — the UI action is real, only the "LinkedIn API"/
 * generated-shareable-URL part is out of scope, per the task brief.
 *
 * [certificateId] is included verbatim (never reformatted — same "opaque string" rule as everywhere
 * else this id is handled) as a plain human-readable credential reference in the shared text, not as
 * a URL (there is no verify-by-id endpoint to link to).
 */
internal fun shareCertificate(context: Context, shareText: String) {
    ShareCompat.IntentBuilder(context)
        .setType("text/plain")
        .setText(shareText)
        .startChooser()
}
