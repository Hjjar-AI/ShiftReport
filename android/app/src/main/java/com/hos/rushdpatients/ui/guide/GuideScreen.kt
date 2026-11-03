package com.hos.rushdpatients.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.AppText
import com.hos.rushdpatients.ui.components.AppTextRole
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.LocalClinicalColors
import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.ward.WardAdaptivePanes
import com.hos.rushdpatients.ui.ward.rememberWardFoldingFeature

/** Shared read-only help for the live and demo navigation hosts. */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val text = rememberUiStrings()
    val clinical = LocalClinicalColors.current
    val colors = MaterialTheme.colorScheme
    val foldingFeature = rememberWardFoldingFeature()
    BackHandler(onBack = onBack)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(topBar = { AppTopBar(text(R.string.guide_title), onBack) }) { padding ->
            WardAdaptivePanes(
                enableSplit = false, foldingFeature = foldingFeature,
                listFraction = UiLayout.defaultListFraction, onListFractionChange = {},
                modifier = Modifier.fillMaxSize().padding(padding),
                primaryTitle = text(R.string.guide_title),
                primary = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(
                            Modifier.widthIn(max = UiLayout.contentMaxWidth).fillMaxWidth()
                                .verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.section)
                        ) {
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = colors.primaryContainer,
                                    contentColor = colors.onPrimaryContainer
                                )
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(UiSpacing.section),
                                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.screen),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppIcons.HelpOutline, contentDescription = null,
                                        modifier = Modifier.size(UiSize.icon))
                                    AppText(text(R.string.guide_intro), modifier = Modifier.weight(1f),
                                        color = colors.onPrimaryContainer)
                                }
                            }
                            AppSection(text(R.string.guide_card_stripe_title)) {
                                AppText(text(R.string.guide_card_stripe_intro))
                                CardColorMeaning(text(R.string.guide_card_red),
                                    text(R.string.guide_card_red_meaning), clinical.urgent)
                                CardColorMeaning(text(R.string.guide_card_amber),
                                    text(R.string.guide_card_amber_meaning), clinical.warning)
                                CardColorMeaning(text(R.string.guide_card_purple),
                                    text(R.string.guide_card_purple_meaning), clinical.pending)
                                AppText(text(R.string.guide_card_no_stripe))
                                AppText(text(R.string.guide_card_stripe_precedence), role = AppTextRole.BODY_STRONG)
                                AppText(text(R.string.guide_card_theme_shades), role = AppTextRole.SUPPORTING)
                            }
                            AppSection(text(R.string.guide_card_star_title)) {
                                Row(
                                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.screen),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(AppIcons.Star, contentDescription = null,
                                        tint = clinical.warning, modifier = Modifier.size(UiSize.icon))
                                    AppText(text(R.string.guide_card_star_meaning), modifier = Modifier.weight(1f))
                                }
                            }
                            AppSection(text(R.string.guide_card_outline_title)) {
                                AppText(text(R.string.guide_card_outline_normal))
                                AppCard(
                                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                                    border = UiBorder.stroke(selected = true),
                                    colors = CardDefaults.cardColors(
                                        containerColor = colors.primaryContainer,
                                        contentColor = colors.onPrimaryContainer
                                    )
                                ) {
                                    Column(Modifier.padding(UiSpacing.screen),
                                        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                        AppText(text(R.string.guide_card_outline_highlight),
                                            role = AppTextRole.BODY_STRONG, color = colors.onPrimaryContainer)
                                        AppText(text(R.string.guide_card_outline_highlight_meaning),
                                            color = colors.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CardColorMeaning(label: String, description: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(UiSize.statusEdge).fillMaxHeight().background(color))
            Column(Modifier.weight(1f).padding(UiSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                AppText(label, role = AppTextRole.BODY_STRONG)
                AppText(description)
            }
        }
    }
}
