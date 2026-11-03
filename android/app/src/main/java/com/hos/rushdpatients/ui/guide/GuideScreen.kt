package com.hos.rushdpatients.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.AppText
import com.hos.rushdpatients.ui.components.AppTextRole
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.LocalClinicalColors
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.ward.WardAdaptivePanes
import com.hos.rushdpatients.ui.ward.rememberWardFoldingFeature

/** Standalone guide content; its navigation entry is deferred by the owner. */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val text = rememberUiStrings()
    val clinical = LocalClinicalColors.current
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
                                AppText(text(R.string.guide_card_star_meaning))
                            }
                            AppSection(text(R.string.guide_card_outline_title)) {
                                AppText(text(R.string.guide_card_outline_normal))
                                CardColorMeaning(text(R.string.guide_card_outline_highlight),
                                    text(R.string.guide_card_outline_highlight_meaning), MaterialTheme.colorScheme.primary)
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
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.screen),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.width(UiSize.statusEdge).height(UiSize.iconXl)
            .background(color, MaterialTheme.shapes.extraSmall))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            AppText(label, role = AppTextRole.BODY_STRONG)
            AppText(description)
        }
    }
}
