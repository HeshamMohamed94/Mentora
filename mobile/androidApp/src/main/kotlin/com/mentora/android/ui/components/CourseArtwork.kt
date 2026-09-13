package com.mentora.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraRadiusTokens

/**
 * `design-to-code/shared/artwork.json` — the governed 5-motif course-artwork system. Governs every
 * place a course thumbnail renders (CourseCard, CourseProgressCard, Course Details hero, Checkout
 * line item, Learning Path Details course rows — later tasks wire real course data into
 * [CourseThumbnail]/[CourseArtwork] below).
 *
 * **Composition recipe implemented here (artwork.json `compositionRecipe`):**
 * 1. Base — a dark purple/indigo `linear-gradient(135deg, C1 0%, C2 X%, C3 100%)`, one of 5.
 * 2. Motif — one centered white geometric icon naming the subject.
 * 3. Light source — a soft directional radial highlight for depth.
 * 4. Category chip — overlaid on the artwork's own scrim, pinned to the logical-start corner
 *    ([CourseArtworkWithChip], not this file's [CourseArtwork] itself — the chip composition lives
 *    one level up so [CourseArtwork] stays a pure "just the artwork" primitive).
 *
 * **Disclosed simplification — fine texture layers omitted.** Every motif's `gradient` string in
 * `artwork.json` also layers a `repeating-linear-gradient`/`repeating-radial-gradient` fine line/dot
 * texture on top of the base+highlight (e.g. `analytics`'s vertical stripe texture, `code`'s dot
 * texture). Compose has no direct primitive for an infinitely-repeating fine CSS pattern brush —
 * reproducing it exactly would mean a custom per-motif `Canvas` tiling loop for a purely decorative,
 * very subtle (≤0.16 alpha) detail. Per the task brief's own explicit allowance, this is an
 * **acceptable, disclosed simplification**: the two most important layers (the exact dominant
 * `linear-gradient` base, transcribed stop-for-stop from `artwork.json`, and the radial highlight at
 * its exact cited center/alpha) are implemented faithfully, and — per `artwork.json`'s own
 * `constraintsPreventingGenericSameness` — the 5 distinct motif icons + deterministic per-category
 * gradient assignment already satisfy the "not a generic purple rectangle" constraint without the
 * fine texture. The texture is not silently dropped; it is this comment.
 *
 * **Gradient-angle approximation.** Compose's [Brush.linearGradient] has no CSS-angle parameter —
 * [start]/[end] are always absolute pixel offsets. A plain top-left→bottom-right diagonal
 * ([Offset.Zero] to `Offset(size.width, size.height)`) is the closest Compose primitive to CSS's
 * `135deg` (which, for a roughly-16:9 box, points from upper-left toward lower-right, the same
 * general direction as that diagonal) — an approximation, not an exact angle match, disclosed here
 * rather than silently assumed equivalent.
 */
enum class CourseMotif(
    /** Centered white motif icon (already-ported [MentoraIcons]/[MentoraIconName] set). */
    val icon: MentoraIconName,
    /** `linear-gradient(135deg, ...)` base layer stops, transcribed verbatim from `artwork.json`. */
    val baseStops: List<Pair<Float, Color>>,
    /** `radial-gradient(circle at X% Y%, ...)` light-source center, as a fraction of the artwork's own size. */
    val highlightCenterFraction: Offset,
    /** The highlight's `rgba(255,255,255,A)` alpha. */
    val highlightAlpha: Float,
    /** The highlight's `transparent R%` radius, as a fraction of the artwork's longer side. */
    val highlightRadiusFraction: Float,
) {
    Analytics(
        icon = MentoraIconName.CourseAnalytics,
        baseStops = listOf(0f to Color(0xFF241C5C), 0.58f to Color(0xFF4A3EB0), 1f to Color(0xFF4A62F0)),
        highlightCenterFraction = Offset(0.84f, 0.16f),
        highlightAlpha = 0.22f,
        highlightRadiusFraction = 0.48f,
    ),
    Design(
        icon = MentoraIconName.CourseDesign,
        baseStops = listOf(0f to Color(0xFF35257F), 0.60f to Color(0xFF6558D3), 1f to Color(0xFF7C4DFF)),
        highlightCenterFraction = Offset(0.76f, 0.78f),
        highlightAlpha = 0.20f,
        highlightRadiusFraction = 0.42f,
    ),
    Code(
        icon = MentoraIconName.CourseCode,
        baseStops = listOf(0f to Color(0xFF1C2470), 0.50f to Color(0xFF3B4278), 1f to Color(0xFF4A62F0)),
        highlightCenterFraction = Offset(0.72f, 0.50f),
        highlightAlpha = 0.22f,
        highlightRadiusFraction = 0.30f,
    ),
    Grid(
        icon = MentoraIconName.CourseGrid,
        baseStops = listOf(0f to Color(0xFF3B2A7A), 0.55f to Color(0xFF6558D3), 1f to Color(0xFF7C4DFF)),
        highlightCenterFraction = Offset(0.20f, 0.20f),
        highlightAlpha = 0.20f,
        highlightRadiusFraction = 0.46f,
    ),
    Layers(
        icon = MentoraIconName.CourseLayers,
        baseStops = listOf(0f to Color(0xFF191A20), 0.55f to Color(0xFF2B2170), 1f to Color(0xFF3B4278)),
        highlightCenterFraction = Offset(0.18f, 0.82f),
        highlightAlpha = 0.18f,
        highlightRadiusFraction = 0.44f,
    ),
}

/**
 * `artwork.json`'s `assignmentRule`, transcribed exactly: `hash = (hash*31 + charCode) >>> 0` over
 * the seed string, `motif index = hash % 5`. Kotlin's [Int] arithmetic wraps at 32 bits (two's
 * complement) on overflow, producing the identical bit pattern JS's per-iteration `>>> 0` truncation
 * would — the only step that needs an explicit unsigned reinterpretation is the final `% 5`, since a
 * negative signed [Int] and its unsigned 32-bit bit-twin are NOT congruent mod 5 for the same bits
 * (2^32 ≡ 1 mod 5, not 0) — hence the explicit `.toUInt()` before `% 5u` below, not a plain `% 5`.
 *
 * `internal` (not `private`) so the plain-JVM determinism unit test can call it directly, alongside
 * [motifFor] itself.
 */
internal fun courseArtworkHash(seed: String): Int {
    var hash = 0
    for (ch in seed) {
        hash = hash * 31 + ch.code
    }
    return hash
}

/**
 * `artwork.json`'s `assignmentRule` in full: deterministic per-seed, never random per render, so the
 * same course/category always renders the same motif everywhere it appears. Callers pass
 * `categoryId` (preferred) falling back to `courseId` — see [CourseThumbnail]'s own seed-selection.
 */
fun motifFor(seed: String): CourseMotif {
    val index = (courseArtworkHash(seed).toUInt() % 5u).toInt()
    return CourseMotif.entries[index]
}

/**
 * Renders just the base+highlight gradient and centered motif icon (layers 1-3 of the composition
 * recipe) — no category chip (layer 4, [CourseArtworkWithChip]) and no image-fallback logic
 * ([CourseThumbnail]). 16:9 aspect ratio per `artwork.json`'s own convention ("scales from an 88px
 * list thumbnail to a full-width hero without re-composition").
 */
@Composable
fun CourseArtwork(
    motif: CourseMotif,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .drawWithCache {
                val baseBrush = Brush.linearGradient(
                    colorStops = motif.baseStops.toTypedArray(),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val highlightBrush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = motif.highlightAlpha), Color.Transparent),
                    center = Offset(
                        size.width * motif.highlightCenterFraction.x,
                        size.height * motif.highlightCenterFraction.y,
                    ),
                    radius = (maxOf(size.width, size.height) * motif.highlightRadiusFraction).coerceAtLeast(1f),
                )
                onDrawBehind {
                    drawRect(baseBrush)
                    drawRect(highlightBrush)
                }
            }
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        MentoraIcon(
            name = motif.icon,
            // Decorative here — the Box above already carries the accessible name (or the caller's
            // own contentDescription lives one level up on CourseThumbnail's AsyncImage/Box).
            contentDescription = null,
            size = MentoraDimens.iconSize.large,
            tint = Color.White,
        )
    }
}

/**
 * `artwork.json`'s `fallbackBehavior`: "the artwork system itself IS the fallback, not a secondary
 * fallback behind it." If [thumbnailUrl] is present, loads it via Coil; on load failure, swaps to
 * [CourseArtwork] in place — never a generic broken-image icon.
 *
 * **Disclosed Android note on the web spec's detection method.** `artwork.json`'s `detection` field
 * describes a web-specific gotcha: an `<img>` can return a 200 response with `naturalWidth === 0`
 * (a broken image that never fires a plain `onError`), needing an extra `onLoad`/rAF check on top of
 * `onError`. Coil's decode pipeline doesn't have that failure mode — a successful [AsyncImagePainter]
 * `Success` state always wraps a decoded, non-zero-dimension `Drawable`/`Bitmap`; a corrupt/
 * undecodable response surfaces as Coil's own `Error` state instead. So on Android, listening to
 * [AsyncImagePainter.State.Error] alone covers the same failure class the web multi-check exists
 * for — not a lesser check, just a platform-appropriate one.
 *
 * [mediaId] is accepted for a later task (real SDK media wiring, Tasks 9+) to pass alongside a
 * resolved [thumbnailUrl] — used here only as Coil's request memory-cache key (stabilizing the cache
 * entry across a URL change for the same underlying media, e.g. a signed-URL refresh) — building the
 * actual media URL from a bare `mediaId` is that later task's job, not this presentational
 * component's.
 */
@Composable
fun CourseThumbnail(
    mediaId: String?,
    thumbnailUrl: String?,
    seed: String,
    categoryId: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // artwork.json assignmentRule: categoryId preferred, courseId (seed) fallback.
    val effectiveSeed = categoryId?.takeIf { it.isNotBlank() } ?: seed
    val motif = remember(effectiveSeed) { motifFor(effectiveSeed) }
    val resolvedUrl = thumbnailUrl?.takeIf { it.isNotBlank() }
    var loadFailed by remember(resolvedUrl) { mutableStateOf(false) }

    Box(modifier = modifier.aspectRatio(16f / 9f)) {
        if (resolvedUrl != null && !loadFailed) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(resolvedUrl, mediaId) {
                    ImageRequest.Builder(context)
                        .data(resolvedUrl)
                        .memoryCacheKey(mediaId ?: resolvedUrl)
                        .build()
                },
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { state -> loadFailed = state is AsyncImagePainter.State.Error },
            )
        } else {
            CourseArtwork(motif = motif, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Top-corners-only clip for [CourseThumbnail]/[CourseArtwork] when embedded as a card's top slice —
 *  e.g. `CourseCard`'s thumbnail (radius.large top corners, square bottom corners where the card body
 *  begins). Exposed once here so every card built in this task shares one shape instance. */
val CourseThumbnailTopCornersShape: Shape = RoundedCornerShape(
    topStart = MentoraRadiusTokens.large,
    topEnd = MentoraRadiusTokens.large,
)

/**
 * `artwork.json`'s `categoryChipOverlay` (layer 4 of the composition recipe): [CourseThumbnail] with
 * [CategoryChip] (`onImageOverlay = true`, already built in Task 5 — reused, not rebuilt) pinned to
 * the *logical*-start corner over the artwork's own scrim. [Alignment.TopStart] is Compose's
 * direction-aware alignment (flips for RTL automatically), which is exactly "logical start," not a
 * hardcoded physical corner.
 */
@Composable
fun CourseArtworkWithChip(
    seed: String,
    categoryId: String?,
    categoryLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    mediaId: String? = null,
    thumbnailUrl: String? = null,
    thumbnailShape: Shape? = null,
) {
    Box(
        modifier = modifier
            // The aspect ratio must be applied here, on the outermost container, so this Box's own
            // height is self-determined (16:9 of whatever width it's given) rather than inherited from
            // a size-unconstrained ancestor (e.g. a plain Column, a LazyRow item, a Row grid cell) —
            // see the Finding-1 fix note on CourseThumbnail's own fillMaxSize() below.
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .let { if (thumbnailShape != null) it.clip(thumbnailShape) else it },
    ) {
        CourseThumbnail(
            mediaId = mediaId,
            thumbnailUrl = thumbnailUrl,
            seed = seed,
            categoryId = categoryId,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
        CategoryChip(
            label = categoryLabel,
            selected = false,
            onImageOverlay = true,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(MentoraDimens.spacing.space2),
        )
    }
}
