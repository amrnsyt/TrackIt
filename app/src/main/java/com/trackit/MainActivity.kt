package com.trackit

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.absoluteValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        intent.getStringExtra("txn")?.let { Store.openId.value = it }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val mode = Store.theme.value
            val dark = mode == "DARK" || (mode == "SYSTEM" && isSystemInDarkTheme())
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }
            TrackItTheme(mode) { App() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("txn")?.let { Store.openId.value = it }
    }
}

private fun listenerOn(c: android.content.Context): Boolean =
    Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
        ?.contains(ComponentName(c, PayListener::class.java).flattenToString()) == true

fun rm(v: Double) = "RM %,.2f".format(v)
fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))
fun periodLabel(c: Category) = when (c.period) {
    "DAILY" -> "Daily"; "WEEKLY" -> "Weekly"; "YEARLY" -> "Yearly"; else -> "Monthly · day ${c.startDay}"
}
fun dayLabel(t: Long): String {
    val now = Calendar.getInstance(); val d = Calendar.getInstance().apply { timeInMillis = t }
    fun same() = now.get(Calendar.YEAR) == d.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)
    if (same()) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (same()) return "Yesterday"
    return SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(t))
}

fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"
}
fun weekDays(): List<Long> {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }
    return (0..6).map { val x = c.timeInMillis; c.add(Calendar.DAY_OF_MONTH, 1); x }
}

fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.55f, stiffness = 450f), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = ripple(), onClick = onClick)
}

@Composable
fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val col by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "nc")
    val pill by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent, label = "np")
    Column(Modifier.clip(MaterialTheme.shapes.medium).bounceClick(onClick).padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(CircleShape).background(pill).padding(horizontal = 16.dp, vertical = 4.dp)) { Icon(icon, null, tint = col) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = col, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun BottomBar(page: Int, go: (Int) -> Unit, onAdd: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit, theme: String, onTheme: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavItem(Icons.Filled.Home, "Home", page == 0) { go(0) }
            NavItem(Icons.Filled.PieChart, "Budgets", page == 1) { go(1) }
            FloatingActionButton(onClick = onAdd, modifier = Modifier.size(58.dp), shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Filled.Add, "Add payment") }
            NavItem(Icons.Filled.History, "History", page == 2) { go(2) }
            Box {
                NavItem(Icons.Filled.MoreHoriz, "More", false) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Backup data") }, leadingIcon = { Icon(Icons.Filled.Upload, null) }, onClick = { menu = false; onBackup() })
                    DropdownMenuItem(text = { Text("Restore data") }, leadingIcon = { Icon(Icons.Filled.Download, null) }, onClick = { menu = false; onRestore() })
                    HorizontalDivider()
                    listOf(Triple("SYSTEM", "Theme: System", Icons.Filled.SettingsBrightness), Triple("LIGHT", "Theme: Light", Icons.Filled.LightMode), Triple("DARK", "Theme: Dark", Icons.Filled.DarkMode)).forEach { (k, l, ic) ->
                        DropdownMenuItem(text = { Text(l) }, leadingIcon = { Icon(ic, null) }, trailingIcon = { if (theme == k) Icon(Icons.Filled.Check, "Selected") }, onClick = { menu = false; onTheme(k) })
                    }
                }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val pager = rememberPagerState { 3 }
    var enabled by remember { mutableStateOf(listenerOn(ctx)) }
    var addT by remember { mutableStateOf(false) }
    var catSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    fun say(m: String) { scope.launch { snack.showSnackbar(m) } }
    val del: (Txn) -> Unit = { t ->
        Store.delTxn(t)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar("Payment deleted", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) Store.addManual(t)
        }
    }
    val bk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { say(if (runCatching { ctx.contentResolver.openOutputStream(it)!!.use { o -> o.write(Store.backup().toByteArray()) } }.isSuccess) "Backup saved" else "Backup failed") }
    }
    val rs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { say(if (runCatching { Store.restore(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText()) }.isSuccess) "Data restored" else "Not a valid backup file") }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { enabled = listenerOn(ctx); Store.rollover() } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val go: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { BottomBar(pager.currentPage, go, { addT = true }, { bk.launch("TrackIt-backup.json") }, { rs.launch(arrayOf("*/*")) }, Store.theme.value) { Store.setTheme(it) } }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Crossfade(pager.currentPage, label = "title") { p ->
                        Text(when (p) { 0 -> greeting(); 1 -> "Budgets"; else -> "History" }, style = MaterialTheme.typography.headlineLarge)
                    }
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(onClick = {
                    val open = { ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); Unit }
                    if (!enabled) open() else scope.launch {
                        snack.currentSnackbarData?.dismiss()
                        if (snack.showSnackbar("Tracking is on. Payment alerts are being recorded.", "Settings", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) open()
                    }
                },
                    modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (enabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            if (enabled) "Tracking is on" else "Tracking is off, tap to enable",
                            tint = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                    }
                }
            }
            AnimatedVisibility(!enabled) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Payments aren't being tracked", fontWeight = FontWeight.Bold)
                            Text("Allow notification access to detect payment alerts.", style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton({ ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }) { Text("Enable") }
                    }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                Box(Modifier.graphicsLayer {
                    val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    alpha = 1f - off * 0.5f; scaleX = 1f - off * 0.05f; scaleY = 1f - off * 0.05f
                }) {
                    when (page) {
                        0 -> Activity(del, { go(1) })
                        1 -> Budgets({ editing = it; catSheet = true }, { editing = null; catSheet = true })
                        else -> History()
                    }
                }
            }
        }
    }
    if (addT) AddTxnSheet { addT = false }
    if (catSheet) CatSheet(editing) { catSheet = false }
    Store.openId.value?.let { id ->
        val t = Store.txns.find { it.id == id }
        if (t != null) { EditTxnSheet(t, del) { Store.openId.value = null } } else { Store.openId.value = null }
    }
}

@Composable
fun DayChip(d: Long, selected: Boolean, has: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(MaterialTheme.shapes.medium).bounceClick(onClick).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(SimpleDateFormat("EEE", Locale.getDefault()).format(Date(d)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
            Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(d)), fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(if (has) MaterialTheme.colorScheme.primary else Color.Transparent))
    }
}

private val pastels = listOf(0xFFF6D5CC, 0xFFDCD6FA, 0xFFE3DFD0, 0xFFD3E8CF).map { Color(it) }
private val ink = Color(0xFF2B2620)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Activity(onDelete: (Txn) -> Unit, goBudgets: () -> Unit) {
    var pick by remember { mutableStateOf<Txn?>(null) }
    var sel by remember { mutableStateOf<Long?>(null) }
    val week = remember { weekDays() }
    val now = System.currentTimeMillis()
    val today = week.firstOrNull { now - it in 0 until 86400000L } ?: week[0]
    val rows = Store.cats.map { it to Store.spent(it) }
    val monthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val unc = Store.txns.filter { it.category == "Uncategorized" && it.time >= monthStart }.sumOf { it.amount }
    val total = rows.sumOf { it.second } + unc
    val budgeted = rows.filter { it.first.budget > 0 }
    val bTotal = budgeted.sumOf { it.first.budget }; val bSpent = budgeted.sumOf { it.second }
    val shown by animateFloatAsState(total.toFloat(), tween(600), label = "total")
    val needs = Store.txns.count { it.category == "Uncategorized" }
    val list0 = sel?.let { s -> Store.txns.filter { it.time >= s && it.time < s + 86400000L } } ?: Store.txns
    val groups = list0.groupBy { dayLabel(it.time) }
    val cards = rows.filter { it.second > 0 || it.first.budget > 0 }.sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { d -> DayChip(d, (sel ?: today) == d, Store.txns.any { it.time >= d && it.time < d + 86400000L }) { sel = if (sel == d) null else d } }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Column(Modifier.padding(24.dp).animateContentSize()) {
                    Text("Spent this period", style = MaterialTheme.typography.labelLarge)
                    Text(rm(shown.toDouble()), style = MaterialTheme.typography.displayMedium)
                    if (bTotal > 0) {
                        Spacer(Modifier.height(12.dp))
                        BudgetBar(bSpent, bTotal, color = MaterialTheme.colorScheme.onPrimaryContainer, track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        Text("${statusText(bSpent, bTotal)} of ${rm(bTotal)} budget", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("${Store.txns.size} payments", Color.White.copy(alpha = 0.55f), MaterialTheme.colorScheme.onPrimaryContainer)
                        if (needs > 0) Pill("$needs need a category", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        if (cards.isNotEmpty()) {
            item { SectionLabel("Categories") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(cards, key = { _, p -> p.first.name }) { i, (c, v) ->
                        Card(modifier = Modifier.width(172.dp), shape = MaterialTheme.shapes.large, onClick = goBudgets,
                            colors = CardDefaults.cardColors(containerColor = pastels[i % pastels.size], contentColor = ink)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (c.budget > 0) "${rm(v)} of ${rm(c.budget)}" else "${rm(v)} spent", style = MaterialTheme.typography.bodySmall)
                                if (c.budget > 0) BudgetBar(v, c.budget, color = ink, track = ink.copy(alpha = 0.12f)) else Spacer(Modifier.height(10.dp))
                                Pill(periodLabel(c), Color.White, ink)
                            }
                        }
                    }
                }
            }
        }
        item { SectionLabel(if (sel == null) "Recent activity" else dayLabel(sel!!)) }
        if (list0.isEmpty()) item {
            EmptyState(Icons.Filled.Contactless, if (sel == null) "No payments yet" else "No payments this day",
                if (sel == null) "Pay with QR or Paywave and it appears here automatically, or tap + to add one." else "Tap the selected day again to see everything.")
        }
        groups.forEach { (day, list) ->
            if (sel == null) item(key = "h$day") { SectionLabel(day) }
            items(list, key = { it.id }) { t ->
                val st = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(t); true } else false })
                SwipeToDismissBox(state = st, modifier = Modifier.animateItem().clip(MaterialTheme.shapes.large), enableDismissFromStartToEnd = false, backgroundContent = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }) {
                    Surface(onClick = { pick = t }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            CatAvatar(t.category)
                            Column(Modifier.weight(1f)) {
                                Text(t.merchant, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (t.category == "Uncategorized") Pill("Categorize", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    else Text(t.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("· " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(rm(t.amount), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
    pick?.let { EditTxnSheet(it, onDelete) { pick = null } }
}

@Composable
fun Budgets(onEdit: (Category) -> Unit, onNew: () -> Unit) {
    val rows = Store.cats.map { it to Store.spent(it) }
    val total = rows.sumOf { it.second }
    val top = rows.filter { it.second > 0 || it.first.budget > 0 }.sortedByDescending { it.second }.take(8)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rm(total), style = MaterialTheme.typography.displayMedium)
                Text("spent across ${Store.cats.size} categories this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                    Text("Bars show budget used, or share of spending when no budget is set.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    if (top.isEmpty()) Text("Add payments to see your chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        top.forEach { (c, v) ->
                            val frac = if (c.budget > 0) (v / c.budget).toFloat() else if (total > 0) (v / total).toFloat() else 0f
                            PillBar(c.name, frac, catColor(c.name), "${(frac * 100).toInt()}%")
                        }
                    }
                }
            }
        }
        if (Store.cats.isEmpty()) item { EmptyState(Icons.Filled.PieChart, "No categories", "Create a category to group payments and set a budget.") }
        items(Store.cats.toList(), key = { it.name }) { c ->
            val v = Store.spent(c)
            Card(modifier = Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large, onClick = { onEdit(c) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CatAvatar(c.name)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(periodLabel(c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (c.budget > 0) {
                        BudgetBar(v, c.budget)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${rm(v)} of ${rm(c.budget)}", style = MaterialTheme.typography.bodyMedium)
                            Text(statusText(v, c.budget), style = MaterialTheme.typography.labelLarge,
                                color = if (v > c.budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                        }
                    } else Text("${rm(v)} spent · no budget set", style = MaterialTheme.typography.bodyMedium)
                    if (c.keywords.isNotBlank()) Text(c.keywords, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)); PrimaryButton("Create a new category") { onNew() } }
    }
}

@Composable
fun History() {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
        if (Store.snaps.isEmpty()) item { EmptyState(Icons.Filled.History, "Nothing here yet", "When a budget period ends, its summary is saved here automatically.") }
        items(Store.snaps.toList(), key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CatAvatar(s.category)
                        Column(Modifier.weight(1f)) {
                            Text(s.category, style = MaterialTheme.typography.titleMedium)
                            Text(fmtDate(s.start) + " – " + fmtDate(s.end - 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.budget > 0) {
                            val over = s.spent > s.budget
                            Pill(if (over) "Over budget" else "On track", if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    if (s.budget > 0) { BudgetBar(s.spent, s.budget); Text("${rm(s.spent)} of ${rm(s.budget)} · ${statusText(s.spent, s.budget)}", style = MaterialTheme.typography.bodyMedium) }
                    else Text("${rm(s.spent)} spent", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun Field(v: String, onChange: (String) -> Unit, label: String, kb: KeyboardType = KeyboardType.Text, err: String? = null) =
    OutlinedTextField(v, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = kb != KeyboardType.Text || label == "Name" || label == "Merchant" || label.startsWith("Merchant"),
        isError = err != null, supportingText = { if (err != null) Text(err) }, shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = kb))

@Composable
fun EditTxnSheet(t: Txn, onDelete: (Txn) -> Unit, close: () -> Unit) {
    var amt by remember(t.id) { mutableStateOf("%.2f".format(t.amount)) }
    var mer by remember(t.id) { mutableStateOf(t.merchant) }
    var cat by remember(t.id) { mutableStateOf(t.category) }
    val v = amt.replace(",", ".").toDoubleOrNull()
    Sheet("Edit payment", close) {
        Field(amt, { amt = it }, "Amount RM", KeyboardType.Decimal, if (v == null || v <= 0) "Enter a valid amount" else null)
        Field(mer, { mer = it }, "Merchant")
        SectionLabel("Category")
        Chips((Store.cats.map { it.name } + "Uncategorized").map { it to it }, cat) { cat = it }
        PrimaryButton("Save changes", v != null && v > 0) { Store.updateTxn(t, t.copy(amount = v!!, merchant = mer.trim().ifBlank { t.merchant }, category = cat)); close() }
        TextButton({ close(); onDelete(t) }, Modifier.fillMaxWidth()) { Text("Delete payment", color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxnSheet(close: () -> Unit) {
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Uncategorized") }
    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    val v = amt.replace(",", ".").toDoubleOrNull()
    Sheet("Add payment", close) {
        Field(amt, { amt = it }, "Amount RM", KeyboardType.Decimal)
        Field(note, { note = it }, "Merchant / note")
        SectionLabel("Category")
        Chips((Store.cats.map { it.name } + "Uncategorized").map { it to it }, cat) { cat = it }
        OutlinedButton({ pickDate = true }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Event, null); Spacer(Modifier.width(8.dp)); Text(fmtDate(time)) }
        PrimaryButton("Save payment", v != null && v > 0) { Store.addManual(Txn("manual:${System.nanoTime()}", v!!, note.trim().ifBlank { "Manual" }, cat, time)); close() }
    }
    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = time)
        DatePickerDialog({ pickDate = false }, confirmButton = {
            TextButton({
                st.selectedDateMillis?.let { ms ->
                    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                    time = Calendar.getInstance().apply { set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
                }
                pickDate = false
            }) { Text("OK") }
        }) { DatePicker(st) }
    }
}

@Composable
fun CatSheet(edit: Category?, close: () -> Unit) {
    var n by remember { mutableStateOf(edit?.name ?: "") }
    var k by remember { mutableStateOf(edit?.keywords ?: "") }
    var b by remember { mutableStateOf(edit?.budget?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "") }
    var p by remember { mutableStateOf(edit?.period ?: "MONTHLY") }
    var d by remember { mutableStateOf((edit?.startDay ?: 1).toString()) }
    val dup = Store.cats.any { it.name.equals(n.trim(), true) && it.name != edit?.name }
    Sheet(if (edit == null) "New category" else "Edit category", close) {
        Field(n, { n = it }, "Name", err = if (dup) "Name already used" else null)
        Field(k, { k = it }, "Keywords (comma separated)")
        Field(b, { b = it }, "Budget RM (optional)", KeyboardType.Decimal)
        SectionLabel("Budget resets")
        Chips(listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly"), p) { p = it }
        AnimatedVisibility(p == "MONTHLY") { Field(d, { d = it.filter { c -> c.isDigit() }.take(2) }, "Reset on day of month (1-28)", KeyboardType.Number) }
        PrimaryButton("Save category", n.isNotBlank() && !dup) {
            val nc = Category(n.trim(), k.trim(), b.replace(",", ".").toDoubleOrNull() ?: 0.0, p, d.toIntOrNull()?.coerceIn(1, 28) ?: 1, edit?.periodStart ?: 0L)
            if (edit == null) Store.addCat(nc) else Store.editCat(edit, nc)
            close()
        }
        if (edit != null) TextButton({ Store.delCat(edit); close() }, Modifier.fillMaxWidth()) { Text("Delete category", color = MaterialTheme.colorScheme.error) }
    }
}
