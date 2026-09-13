package com.mentora.android.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mentora.android.R
import com.mentora.android.navigation.TabGraph
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconName

/** Test tag for the whole bottom-nav container — `NavigationShellTest` asserts on its
 * presence/absence directly (rather than by text) since screen titles/placeholder body text can
 * repeat a tab's own label (e.g. the Explore top bar title and the Explore tab label are both just
 * "Explore"). */
const val MobileBottomNavigationTestTag = "MobileBottomNavigation"

/**
 * `design-to-code/shared/navigation.json#/shells/mobileStudentShell` — the 5-item bottom nav.
 * Exactly 5 items, fixed order, no badges (MVP has no notification system per
 * `product/MVP_SCOPE.md`). Visual values per that spec's `visualValues`: height 64dp + safe-area
 * inset, `surface.default` background, top-edge-only `border.default`, 24dp icons, `caption`
 * typography labels, `brand.primary` active / `text.secondary` inactive.
 *
 * Built as a plain `Row`/`Column` rather than Material3's `NavigationBar`/`NavigationBarItem` —
 * M3's default active-item pill/indicator background has no equivalent in the locked spec (top
 * border only, plain tint change, no indicator shape), so a custom layout gives exact control
 * without fighting M3 defaults.
 *
 * Labels are real EN/AR string resources (`nav_home`... / `values-ar/strings.xml`) — the Arabic
 * strings were verified at Task 3's showcase extraction pass, not re-translated here.
 */
private data class BottomNavItemSpec(
    val tab: TabGraph,
    val icon: MentoraIconName,
    val labelResId: Int,
    val testTag: String,
)

// "home" reuses Dashboard's icon glyph — the ported 42-icon set (MentoraIcons.kt, D80) has no
// separate "Home" silhouette, and neither does web's own icon.tsx source it was ported from; Home
// and Dashboard have always shared one icon in this design system.
private val BottomNavItems = listOf(
    BottomNavItemSpec(TabGraph.HomeGraph, MentoraIconName.Dashboard, R.string.nav_home, "bottom_nav_home"),
    BottomNavItemSpec(TabGraph.ExploreGraph, MentoraIconName.Explore, R.string.nav_explore, "bottom_nav_explore"),
    BottomNavItemSpec(TabGraph.MyLearningGraph, MentoraIconName.MyLearning, R.string.nav_my_learning, "bottom_nav_my_learning"),
    BottomNavItemSpec(TabGraph.AiTutorGraph, MentoraIconName.AiTutor, R.string.nav_ai_tutor, "bottom_nav_ai_tutor"),
    BottomNavItemSpec(TabGraph.ProfileGraph, MentoraIconName.Profile, R.string.nav_profile, "bottom_nav_profile"),
)

@Composable
fun MobileBottomNavigation(
    selectedTab: TabGraph,
    onTabSelected: (TabGraph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = colorScheme.outlineVariant // color.border.default
    val borderWidthPx = with(LocalDensity.current) { MentoraDimens.borderWidthDefault.toPx() }

    Row(
        modifier = modifier
            .testTag(MobileBottomNavigationTestTag)
            .fillMaxWidth()
            .background(colorScheme.surface)
            .drawBehind {
                // border.width.default, top edge only — never the whole outline.
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = borderWidthPx,
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItems.forEach { item ->
            val isSelected = item.tab == selectedTab
            val tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
            val label = stringResource(item.labelResId)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(item.testTag)
                    .selectable(
                        selected = isSelected,
                        onClick = { onTabSelected(item.tab) },
                        role = Role.Tab,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MentoraIcon(
                    // Decorative — the Text label right below it (and the selectable Column's own
                    // Role.Tab semantics) already announce this item; a second contentDescription
                    // on the icon would double-announce the same label.
                    name = item.icon,
                    contentDescription = null,
                    size = MentoraDimens.iconSize.default, // icon.default, 24dp
                    tint = tint,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall, // typography.caption
                    color = tint,
                )
            }
        }
    }
}
