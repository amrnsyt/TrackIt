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
    primary = Color(0xFFFFB81F), onPrimary = Color(0xFF1F1500), primaryContainer = Color(0xFFFFC53D), onPrimaryContainer = Color(0xFF2A1F00),
    secondary = Color(0xFF78715F), secondaryContainer = Color(0xFFD8CEC1), onSecondaryContainer = Color(0xFF2E2820),
    tertiary = Color(0xFF7C9A2D), onTertiary = Color.White, tertiaryContainer = Color(0xFFF6D5CC), onTertiaryContainer = Color(0xFF5A1D12),
    background = Color(0xFFF0EEEA), surface = Color(0xFFF0EEEA), onBackground = Color(0xFF1E1B16), onSurface = Color(0xFF1E1B16), onSurfaceVariant = Color(0xFF6B645A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFFAF8F5),
    surfaceContainerHigh = Color(0xFFE9E5DE), surfaceContainerHighest = Color(0xFFE4DFD6), outlineVariant = Color(0xFFD6D0C6)
)
private val DarkC = darkColorScheme(
    primary = Color(0xFFFFC145), onPrimary = Color(0xFF2A1F00), primaryContainer = Color(0xFFFFC145), onPrimaryContainer = Color(0xFF2A1F00),
    secondary = Color(0xFFB5AD9F), secondaryContainer = Color(0xFF4A433A), onSecondaryContainer = Color(0xFFE8DFD2),
    tertiary = Color(0xFFA3C14A), onTertiary = Color(0xFF1D2600), tertiaryContainer = Color(0xFF5A3A33), onTertiaryContainer = Color(0xFFF6D5CC),
    background = Color(0xFF141210), surface = Color(0xFF141210), onBackground = Color(0xFFEAE4DA), onSurface = Color(0xFFEAE4DA), onSurfaceVariant = Color(0xFFB5AD9F),
    surfaceContainerLowest = Color(0xFF0F0D0B), surfaceContainerLow = Color(0xFF1F1C18), surfaceContainer = Color(0xFF26221D),
    surfaceContainerHigh = Color(0xFF2E2A24), surfaceContainerHighest = Color(0xFF3A352E), outlineVariant = Color(0xFF4A443B)
)

@Composable
fun TrackItTheme(mode: String = "SYSTEM", content: @Composable () -> Unit) {
    val dark = when (mode) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val t = Typography()
    MaterialTheme(
        colorScheme = if (dark) DarkC else LightC,
        typography = t.copy(
            displayLarge = t.displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
            displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
            headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}

private val palette = listOf(0xFFFFB81F, 0xFF744438, 0xFF86A02E, 0xFF78715F, 0xFFD6477A, 0xFF5C7CFA, 0xFFE57C3A, 0xFF8E6BD8).map { Color(it) }
fun catColor(name: String): Color = palette[Math.abs(name.hashCode()) % palette.size]

@Composable
fun CatAvatar(name: String, size: Dp = 44.dp) {
    val c = if (name == "Uncategorized") MaterialTheme.colorScheme.onSurfaceVariant else catColor(name)
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
fun BudgetBar(spent: Double, budget: Double, modifier: Modifier = Modifier, color: Color? = null, track: Color? = null) {
    val f by animateFloatAsState(if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f, tween(700), label = "bar")
    val col = color ?: when {
        budget <= 0 -> MaterialTheme.colorScheme.primary
        spent > budget -> MaterialTheme.colorScheme.error
        spent >= 0.8 * budget -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    LinearProgressIndicator(
        progress = { f }, modifier = modifier.fillMaxWidth().height(10.dp), color = col,
        trackColor = track ?: MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {}
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

@Composable
fun PillBar(label: String, frac: Float, color: Color, text: String) {
    val f by animateFloatAsState(if (frac <= 0f) 0.05f else frac.coerceIn(0.12f, 1f), tween(800), label = "pill")
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(66.dp).height(210.dp).clip(RoundedCornerShape(33.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(f).clip(RoundedCornerShape(33.dp)).background(color), contentAlignment = Alignment.BottomCenter) {
                Text(text, Modifier.padding(bottom = 14.dp), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
