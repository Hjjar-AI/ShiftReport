package com.hos.rushdpatients.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.BuildConfig
import com.hos.rushdpatients.R

private val technicalContributors = listOf("د. أيهم شيخة", "محمد زاهر شقير")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroAboutScreen(splash: Boolean, onBack: (() -> Unit)? = null) {
    if (splash) {
        Surface(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .clickable(onClickLabel = "تخطي المقدمة", onClick = { onBack?.invoke() })
                    .safeDrawingPadding().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIdentity(compact = true)
                    Text("إشراف وتطوير", style = MaterialTheme.typography.labelMedium)
                    Text("محمد علي حجار", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("مساهمة تقنية", style = MaterialTheme.typography.labelMedium)
                    technicalContributors.forEach { name ->
                        Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("اضغط في أي مكان للمتابعة · تُغلق المقدمة بعد ١٠ ثوانٍ",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
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
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppIdentity(compact = false)
                Text("تنظيم بيانات المرضى وتسليم المناوبات وإعداد التقارير عبر تليجرام.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CreditBlock("إشراف وتطوير", "محمد علي حجار", prominent = true)
                        Text("بواسطة الذكاء الاصطناعي", style = MaterialTheme.typography.bodySmall)
                        Divider()
                        CreditBlock("مساهمة تقنية", technicalContributors.joinToString("\n"), prominent = true)
                        Divider()
                        CreditBlock("الفكرة الأولية وبنية التقرير الأساسي", "د. نديم العباس")
                    }
                }
                Text("الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppIdentity(compact: Boolean) {
    Surface(color = Color.White, shape = MaterialTheme.shapes.large) {
        Image(painterResource(R.drawable.app_emblem_transparent),
            contentDescription = "شعار شعبة الطب النفسي", contentScale = ContentScale.Fit,
            modifier = Modifier.size(if (compact) 128.dp else 180.dp).padding(8.dp))
    }
    Text("تقرير المناوبة", style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
    Text("شعبة الطب النفسي", style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CreditBlock(title: String, name: String, prominent: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
