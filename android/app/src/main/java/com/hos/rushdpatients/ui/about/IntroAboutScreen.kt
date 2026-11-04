package com.hos.rushdpatients.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import com.hos.rushdpatients.BuildConfig
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.AppBrandMark

/** Ordinary spaces between people allow wrapping; nonbreaking spaces keep each name together. */
internal fun testerNamesParagraph(names: Array<String>): String =
    names.joinToString("، ") { it.trim().replace(' ', '\u00A0') }

@Composable
fun IntroAboutScreen(
    splash: Boolean,
    onBack: (() -> Unit)? = null,
    onSplashPress: () -> Unit = {},
    onSplashRelease: () -> Unit = {}
) {
    if (splash) {
        SplashIntroScreen(onSkip = { onBack?.invoke() }, onPress = onSplashPress, onRelease = onSplashRelease)
        return
    }
    Scaffold(topBar = {
        AppTopBar(title = stringResource(R.string.about_page_title), onBack = { onBack?.invoke() })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = com.hos.rushdpatients.ui.theme.UiLayout.aboutMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState()).padding(com.hos.rushdpatients.ui.theme.UiSpacing.aboutInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(com.hos.rushdpatients.ui.theme.UiSpacing.largeSection)
            ) {
                IntroCredits()
                AppVersion()
            }
        }
    }
}

@Composable
private fun AppVersion() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(com.hos.rushdpatients.ui.theme.UiSpacing.tiny)) {
        Text(stringResource(R.string.about_version_label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(BuildConfig.VERSION_NAME, modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntroCredits() {
    AppBrandMark(modifier = Modifier.semantics { heading() })
    val subtitle = stringResource(R.string.about_subtitle)
    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CreditBlock(stringResource(R.string.about_supervisor_role), stringResource(R.string.about_supervisor_name), prominent = true)
    CreditBlock(stringResource(R.string.about_technical_role),
        listOf(stringResource(R.string.about_contributors_prefix),
            stringArrayResource(R.array.about_technical_contributors).joinToString("\n"))
            .filter(String::isNotBlank).joinToString("\n"), prominent = true)
    CreditBlock(stringResource(R.string.about_original_role), stringResource(R.string.about_original_author_name))
    CreditBlock(stringResource(R.string.about_testers_role), testerNamesParagraph(stringArrayResource(R.array.about_testers)))
}

@Composable
private fun CreditBlock(title: String, name: String, prominent: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(com.hos.rushdpatients.ui.theme.UiSpacing.small)) {
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
