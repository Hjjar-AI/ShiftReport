package com.hos.rushdpatients.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.BuildConfig
import com.hos.rushdpatients.R

internal val technicalContributors = listOf("أيهم شيخة", "محمد زاهر شقير")
internal const val introSupervisorName = "الطبيب محمد علي حجار"
internal const val introOriginalAuthorName = "الطبيب الاختصاصي نديم العباس"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroAboutScreen(
    splash: Boolean,
    onBack: (() -> Unit)? = null
) {
    if (splash) {
        SplashIntroScreen(onSkip = { onBack?.invoke() })
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("حول التطبيق") }, navigationIcon = {
            IconButton(onClick = { onBack?.invoke() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع",
                    modifier = Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 180f else 0f))
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = com.hos.rushdpatients.ui.theme.UiLayout.aboutMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState()).padding(com.hos.rushdpatients.ui.theme.UiSpacing.aboutInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(com.hos.rushdpatients.ui.theme.UiSpacing.largeSection)
            ) {
                IntroCredits(compact = false)
                AppVersion()
            }
        }
    }
}

@Composable
private fun AppVersion() {
    Text("الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IntroCredits(compact: Boolean) {
    Text("تقرير المناوبة", style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
    Text("(للطب النفسي)", style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CreditBlock("إشراف وتطوير", introSupervisorName, prominent = true)
    Surface(color = Color.White, shape = MaterialTheme.shapes.large) {
        Image(painterResource(R.drawable.app_emblem_transparent),
            contentDescription = "شعار شعبة الطب النفسي", contentScale = ContentScale.Fit,
            modifier = Modifier.size(if (compact) com.hos.rushdpatients.ui.theme.UiSize.aboutEmblemCompact else com.hos.rushdpatients.ui.theme.UiSize.aboutEmblem).padding(com.hos.rushdpatients.ui.theme.UiSpacing.screen))
    }
    CreditBlock("مساهمة تقنية", "الأطباء:\n" + technicalContributors.joinToString("\n"), prominent = true)
    CreditBlock("فكرة Excel الأولية وبنية التقرير الأساسي", introOriginalAuthorName)
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
