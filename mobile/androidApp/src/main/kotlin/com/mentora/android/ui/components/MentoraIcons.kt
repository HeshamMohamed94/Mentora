package com.mentora.android.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens

/**
 * PHASE_4_ANDROID_PLAN.md § 6 G8 / D80 — **disclosed placeholder icon set, not a bug to "fix."**
 *
 * `design-system/DESIGN_SYSTEM.md § Iconography` specifies Material Symbols Rounded (a self-hosted
 * font or SVG sprite) as Mentora's canonical icon language. No such asset — font file, sprite sheet,
 * or build pipeline — exists anywhere in this repository, and this environment cannot download one.
 * Web (`web/src/components/ui/icon.tsx`) already ships the same disclosed workaround: a small
 * hand-drawn, rounded-stroke SVG set with an *equivalent silhouette* to the real icons, behind a
 * stable `Icon` API so the real font can be swapped in later with zero call-site changes.
 *
 * This file is the Compose port of that exact set (D80: "port web's existing 42-icon hand-drawn
 * `ImageVector` set to Compose rather than adding `material-icons-extended`" — keeps Web/Android
 * visually identical and inherits, rather than creates, the icon-fidelity gap). Every path below is
 * transcribed from `web/src/components/ui/icon.tsx`'s `PATHS` map — same 24×24 viewBox, same
 * `strokeWidth 1.6`/round cap/round join outline style (`fill="none" stroke="currentColor"`) for
 * every icon except `moreVert`, whose three dots are filled circles with no stroke in the source
 * (`fill="currentColor" stroke="none"` per-element override) and are ported the same way here via
 * [filledPath] instead of [strokePath]. SVG `<rect rx>`/`<circle>` elements (which have no direct
 * Compose `PathBuilder` primitive) are transcribed as their mathematically equivalent arc-based SVG
 * path-data strings (documented per icon below) rather than approximated freehand.
 *
 * The actual color/tint is intentionally never baked into these paths (both [strokePath] and
 * [filledPath] use an opaque placeholder [Color.Black]) — [MentoraIcon] re-tints the whole vector via
 * `Icon(tint = ...)`'s `ColorFilter.tint` at render time, exactly like `Icon`'s `currentColor` on web,
 * so every icon always renders in whatever `color.text.*`/`color.brand.*` token the call site passes.
 */
private const val StrokeWidthViewportUnits = 1.6f

private fun ImageVector.Builder.strokePath(vararg pathData: String) {
    for (data in pathData) {
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = StrokeWidthViewportUnits,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}

private fun ImageVector.Builder.filledPath(vararg pathData: String) {
    for (data in pathData) {
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }
}

private fun buildIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

/** SVG `<rect x y width height rx>` (ry == rx) as an equivalent rounded-rect path-data string. */
private fun roundedRect(x: Float, y: Float, width: Float, height: Float, rx: Float): String {
    val right = x + width
    val bottom = y + height
    return "M${x + rx},$y H${right - rx} A$rx,$rx 0 0 1 $right,${y + rx} V${bottom - rx} " +
        "A$rx,$rx 0 0 1 ${right - rx},$bottom H${x + rx} A$rx,$rx 0 0 1 $x,${bottom - rx} " +
        "V${y + rx} A$rx,$rx 0 0 1 ${x + rx},$y Z"
}

/** SVG `<circle cx cy r>` as an equivalent two-arc path-data string. */
private fun circlePath(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},$cy a$r,$r 0 1 0 ${2 * r},0 a$r,$r 0 1 0 ${-2 * r},0"

/** The exact `IconName` union from `web/src/components/ui/icon.tsx`, ported 1:1 for cross-reference. */
enum class MentoraIconName {
    Dashboard, Explore, MyLearning, LearningPaths, AiTutor, Certificates, Profile, Settings, Menu,
    Logout, Play, Pause, VolumeOn, VolumeMuted, Fullscreen, FullscreenExit, CheckCircle, Cancel,
    ExpandMore, ExpandLess, Visibility, VisibilityOff, Search, Close, Add, Delete, ArrowUpward,
    ArrowDownward, DragHandle, Upload, CourseAnalytics, CourseDesign, CourseCode, CourseGrid,
    CourseLayers, ArrowForward, ArrowBack, DarkMode, LightMode, People, School, MoreVert,
}

/** One [ImageVector] property per icon, named identically (camelCase) to web's `IconName` union. */
object MentoraIcons {
    val dashboard: ImageVector by lazy {
        buildIcon("dashboard") {
            strokePath(
                roundedRect(3.5f, 3.5f, 7f, 7f, 1.5f),
                roundedRect(13.5f, 3.5f, 7f, 7f, 1.5f),
                roundedRect(3.5f, 13.5f, 7f, 7f, 1.5f),
                roundedRect(13.5f, 13.5f, 7f, 7f, 1.5f),
            )
        }
    }

    val explore: ImageVector by lazy {
        buildIcon("explore") {
            strokePath(circlePath(12f, 12f, 8.5f), "M14.8 9.2l-2 4.7-4.7 2 2-4.7z")
        }
    }

    val myLearning: ImageVector by lazy {
        buildIcon("myLearning") {
            strokePath(
                "M4 5.5C4 4.7 4.7 4 5.5 4H12v16H5.5c-.8 0-1.5-.7-1.5-1.5v-13zM20 5.5c0-.8-.7-1.5-1.5-1.5H12v16h6.5c.8 0 1.5-.7 1.5-1.5v-13z",
            )
        }
    }

    val learningPaths: ImageVector by lazy {
        buildIcon("learningPaths") {
            strokePath(
                circlePath(5f, 6f, 2f),
                circlePath(19f, 18f, 2f),
                "M6.7 7.3C10 11 14 13 17.3 16.7",
            )
        }
    }

    val aiTutor: ImageVector by lazy {
        buildIcon("aiTutor") {
            strokePath(
                "M4 6.5C4 5.1 5.1 4 6.5 4h11C18.9 4 20 5.1 20 6.5v7c0 1.4-1.1 2.5-2.5 2.5H9l-4 4v-4H6.5C5.1 16 4 14.9 4 13.5v-7z",
            )
        }
    }

    val certificates: ImageVector by lazy {
        buildIcon("certificates") {
            strokePath(circlePath(12f, 8.5f, 4.5f), "M9 12.5l-1.5 7 4.5-2.5 4.5 2.5-1.5-7")
        }
    }

    val profile: ImageVector by lazy {
        buildIcon("profile") {
            strokePath(circlePath(12f, 8f, 3.5f), "M5 20c0-3.9 3.1-7 7-7s7 3.1 7 7")
        }
    }

    val settings: ImageVector by lazy {
        buildIcon("settings") {
            strokePath(
                circlePath(12f, 12f, 3f),
                "M12 3v2.5M12 18.5V21M21 12h-2.5M5.5 12H3M18.4 5.6l-1.8 1.8M7.4 16.6l-1.8 1.8M18.4 18.4l-1.8-1.8M7.4 7.4L5.6 5.6",
            )
        }
    }

    val menu: ImageVector by lazy {
        buildIcon("menu") { strokePath("M4 6.5h16M4 12h16M4 17.5h16") }
    }

    val logout: ImageVector by lazy {
        buildIcon("logout") {
            strokePath(
                "M9 4H6.5C5.1 4 4 5.1 4 6.5v11C4 18.9 5.1 20 6.5 20H9",
                "M14 15.5l4.5-3.5-4.5-3.5M18.5 12H9",
            )
        }
    }

    val play: ImageVector by lazy {
        buildIcon("play") { strokePath("M8 5.5v13l10-6.5z") }
    }

    val pause: ImageVector by lazy {
        buildIcon("pause") { strokePath("M8 5.5v13M16 5.5v13") }
    }

    val volumeOn: ImageVector by lazy {
        buildIcon("volumeOn") {
            strokePath(
                "M4 10v4h4l5 4V6L8 10z",
                "M16 9c1.7 1.7 1.7 4.3 0 6M18.5 6.5c3 3 3 8 0 11",
            )
        }
    }

    val volumeMuted: ImageVector by lazy {
        buildIcon("volumeMuted") { strokePath("M4 10v4h4l5 4V6L8 10zM16.5 9.5l4 5M20.5 9.5l-4 5") }
    }

    val fullscreen: ImageVector by lazy {
        buildIcon("fullscreen") { strokePath("M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5") }
    }

    val fullscreenExit: ImageVector by lazy {
        buildIcon("fullscreenExit") { strokePath("M4 9h5V4M20 9h-5V4M4 15h5v5M20 15h-5v5") }
    }

    val checkCircle: ImageVector by lazy {
        buildIcon("checkCircle") { strokePath(circlePath(12f, 12f, 9f), "M8 12.5l2.5 2.5 5.5-6") }
    }

    val cancel: ImageVector by lazy {
        buildIcon("cancel") { strokePath(circlePath(12f, 12f, 9f), "M9 9l6 6M15 9l-6 6") }
    }

    val expandMore: ImageVector by lazy {
        buildIcon("expandMore") { strokePath("M7 9.5l5 5 5-5") }
    }

    val expandLess: ImageVector by lazy {
        buildIcon("expandLess") { strokePath("M7 14.5l5-5 5 5") }
    }

    val visibility: ImageVector by lazy {
        buildIcon("visibility") {
            strokePath(
                "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z",
                circlePath(12f, 12f, 3f),
            )
        }
    }

    val visibilityOff: ImageVector by lazy {
        buildIcon("visibilityOff") {
            strokePath(
                "M4 4l16 16",
                "M9.9 5.7c.7-.13 1.4-.2 2.1-.2 6 0 9.5 6.5 9.5 6.5a17.5 17.5 0 01-3.2 4.1M6.8 7.3A17.6 17.6 0 002.5 12S6 18.5 12 18.5c1.3 0 2.5-.3 3.6-.8",
                "M9.9 14.1a3 3 0 004.2-4.2",
            )
        }
    }

    val search: ImageVector by lazy {
        buildIcon("search") { strokePath(circlePath(10.5f, 10.5f, 6.5f), "M19.5 19.5l-4.3-4.3") }
    }

    val close: ImageVector by lazy {
        buildIcon("close") { strokePath("M6 6l12 12M18 6L6 18") }
    }

    val add: ImageVector by lazy {
        buildIcon("add") { strokePath("M12 5v14M5 12h14") }
    }

    val delete: ImageVector by lazy {
        buildIcon("delete") { strokePath("M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5") }
    }

    val arrowUpward: ImageVector by lazy {
        buildIcon("arrowUpward") { strokePath("M12 19V5M6.5 10.5L12 5l5.5 5.5") }
    }

    val arrowDownward: ImageVector by lazy {
        buildIcon("arrowDownward") { strokePath("M12 5v14M6.5 13.5L12 19l5.5-5.5") }
    }

    val dragHandle: ImageVector by lazy {
        buildIcon("dragHandle") { strokePath("M8 7h8M8 12h8M8 17h8") }
    }

    val upload: ImageVector by lazy {
        buildIcon("upload") { strokePath("M12 16V4M7 9l5-5 5 5M5 20h14") }
    }

    val courseAnalytics: ImageVector by lazy {
        buildIcon("courseAnalytics") { strokePath("M6 20V13M12 20V9M18 20V5") }
    }

    val courseDesign: ImageVector by lazy {
        buildIcon("courseDesign") {
            strokePath(circlePath(8f, 9f, 2.3f), circlePath(16f, 9f, 2.3f), circlePath(12f, 16f, 2.3f))
        }
    }

    val courseCode: ImageVector by lazy {
        buildIcon("courseCode") { strokePath("M9 8l-5 4 5 4M15 8l5 4-5 4") }
    }

    val courseGrid: ImageVector by lazy {
        buildIcon("courseGrid") {
            strokePath(
                roundedRect(4f, 4f, 7f, 7f, 1f),
                roundedRect(13f, 4f, 7f, 7f, 1f),
                roundedRect(4f, 13f, 7f, 7f, 1f),
                roundedRect(13f, 13f, 7f, 7f, 1f),
            )
        }
    }

    val courseLayers: ImageVector by lazy {
        buildIcon("courseLayers") {
            strokePath("M12 4l8 4-8 4-8-4z", "M4 12l8 4 8-4", "M4 16l8 4 8-4")
        }
    }

    val arrowForward: ImageVector by lazy {
        buildIcon("arrowForward") { strokePath("M5 12h14M13 6l6 6-6 6") }
    }

    val arrowBack: ImageVector by lazy {
        buildIcon("arrowBack") { strokePath("M19 12H5M11 6l-6 6 6 6") }
    }

    val darkMode: ImageVector by lazy {
        buildIcon("darkMode") { strokePath("M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z") }
    }

    val lightMode: ImageVector by lazy {
        buildIcon("lightMode") {
            strokePath(
                circlePath(12f, 12f, 5f),
                "M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42",
            )
        }
    }

    val people: ImageVector by lazy {
        buildIcon("people") {
            strokePath(
                circlePath(9f, 8f, 3.2f),
                "M3.5 19c0-3 2.5-5.3 5.5-5.3s5.5 2.3 5.5 5.3",
                "M15.5 5.3a3.2 3.2 0 010 6.2M20.5 19c0-2.6-1.9-4.8-4.5-5.2",
            )
        }
    }

    val school: ImageVector by lazy {
        buildIcon("school") {
            strokePath(
                "M2 8.5L12 4l10 4.5-10 4.5-10-4.5z",
                "M6 10.8v4.8c0 1.5 2.7 2.7 6 2.7s6-1.2 6-2.7v-4.8",
                "M21 9.5v6",
            )
        }
    }

    /** Only icon whose source overrides `fill="currentColor" stroke="none"` per-dot — filled, not stroked. */
    val moreVert: ImageVector by lazy {
        buildIcon("moreVert") {
            filledPath(circlePath(12f, 5.5f, 1.4f), circlePath(12f, 12f, 1.4f), circlePath(12f, 18.5f, 1.4f))
        }
    }

    /** Resolves a [MentoraIconName] to its [ImageVector] — the lookup [MentoraIcon] uses. */
    fun forName(name: MentoraIconName): ImageVector = when (name) {
        MentoraIconName.Dashboard -> dashboard
        MentoraIconName.Explore -> explore
        MentoraIconName.MyLearning -> myLearning
        MentoraIconName.LearningPaths -> learningPaths
        MentoraIconName.AiTutor -> aiTutor
        MentoraIconName.Certificates -> certificates
        MentoraIconName.Profile -> profile
        MentoraIconName.Settings -> settings
        MentoraIconName.Menu -> menu
        MentoraIconName.Logout -> logout
        MentoraIconName.Play -> play
        MentoraIconName.Pause -> pause
        MentoraIconName.VolumeOn -> volumeOn
        MentoraIconName.VolumeMuted -> volumeMuted
        MentoraIconName.Fullscreen -> fullscreen
        MentoraIconName.FullscreenExit -> fullscreenExit
        MentoraIconName.CheckCircle -> checkCircle
        MentoraIconName.Cancel -> cancel
        MentoraIconName.ExpandMore -> expandMore
        MentoraIconName.ExpandLess -> expandLess
        MentoraIconName.Visibility -> visibility
        MentoraIconName.VisibilityOff -> visibilityOff
        MentoraIconName.Search -> search
        MentoraIconName.Close -> close
        MentoraIconName.Add -> add
        MentoraIconName.Delete -> delete
        MentoraIconName.ArrowUpward -> arrowUpward
        MentoraIconName.ArrowDownward -> arrowDownward
        MentoraIconName.DragHandle -> dragHandle
        MentoraIconName.Upload -> upload
        MentoraIconName.CourseAnalytics -> courseAnalytics
        MentoraIconName.CourseDesign -> courseDesign
        MentoraIconName.CourseCode -> courseCode
        MentoraIconName.CourseGrid -> courseGrid
        MentoraIconName.CourseLayers -> courseLayers
        MentoraIconName.ArrowForward -> arrowForward
        MentoraIconName.ArrowBack -> arrowBack
        MentoraIconName.DarkMode -> darkMode
        MentoraIconName.LightMode -> lightMode
        MentoraIconName.People -> people
        MentoraIconName.School -> school
        MentoraIconName.MoreVert -> moreVert
    }
}

/**
 * Renders a [MentoraIconName], analogous to web's `Icon({ name, size, className })`. [tint] defaults
 * to the ambient content color (mirrors `currentColor` on web) and [size] to `icon.default` (24dp,
 * [MentoraDimens.iconSize]) — pass `MentoraDimens.iconSize.medium`/`.small`/`.large` per whichever
 * component-table row is being built (e.g. Buttons/Inputs use `icon.medium`, 20dp).
 *
 * [contentDescription] is required (nullable, but callers must pass `null` explicitly for a
 * genuinely decorative icon) rather than defaulted, so a real label is never forgotten by omission.
 */
@Composable
fun MentoraIcon(
    name: MentoraIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = MentoraDimens.iconSize.default,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = MentoraIcons.forName(name),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
