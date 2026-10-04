package com.trackit

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightC = lightColorScheme(
    primary = Color(0xFF00696B), onPrimary = Color.White, primaryContainer = Color(0xFFB4F0F0), onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363), secondaryContainer = Color(0xFFCCE8E8), onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF8A5100), tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF4FAFA), surface = Color(0xFFF4FAFA), surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF5F5), surfaceContainer = Color(0xFFE8EFEF),
    surfaceContainerHigh = Color(0xFFE3E9E9), surfaceContainerHighest = Color(0xFFDDE4E4), outlineVariant = Color(0xFFBEC8C8)
)
private val DarkC = darkColorScheme(
    primary = Color(0xFF80D5D6), onPrimary = Color(0xFF003737), primaryContainer = Color(0xFF004F50), onPrimaryContainer = Color(0xFFB4F0F0),
    secondary = Color(0xFFB0CCCC), secondaryContainer = Color(0xFF324B4B), onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFFFB86B), tertiaryContainer = Color(0xFF693C00), onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF0E1415), surface = Color(0xFF0E1415), surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1D), surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2C), surfaceContainerHighest = Color(0xFF303637), outlineVariant = Color(0xFF3F4948)
)

@Composable
fun TrackItTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c) } else if (dark) DarkC else LightC
    val t = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = t.copy(
            displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
            headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

private val palette = listOf(0xFF2E9E8F, 0xFFE57C3A, 0xFF5C7CFA, 0xFFD6477A, 0xFF8E6BD8, 0xFFB59A1B, 0xFF3BA55D, 0xFFD9534F).map { Color(it) }

@Composable
fun CatAvatar(name: String, size: Dp = 44.dp) {
    val c = if (name == "Uncategorized") MaterialTheme.colorScheme.onSurfaceVariant else palette[Math.abs(name.hashCode()) % palette.size]
    Box(Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(if (name == "Uncategorized") "?" else name.take(1).uppercase(), color = c, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, contentColor = fg, shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BudgetBar(spent: Double, budget: Double, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f, tween(700), label = "bar")
    val col = when {
        budget <= 0 -> MaterialTheme.colorScheme.primary
        spent > budget -> MaterialTheme.colorScheme.error
        spent >= 0.8 * budget -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { f }, modifier = modifier.fillMaxWidth().height(10.dp), color = col,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {}
    )
}

fun statusText(spent: Double, budget: Double) =
    if (budget <= 0) "" else if (spent > budget) "Over by ${rm(spent - budget)}" else "${rm(budget - spent)} left"

@Composable
fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

@Composable
fun EmptyState(icon: ImageVector, title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(sub, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

@Composable
fun Chips(options: List<Pair<String, String>>, selected: String, onSel: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, l) -> FilterChip(selected = selected == k, onClick = { onSel(k) }, label = { Text(l) }) }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxWidth().height(54.dp), enabled = enabled) { Text(text, fontWeight = FontWeight.SemiBold) }
