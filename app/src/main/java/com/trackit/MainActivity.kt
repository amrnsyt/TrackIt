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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Reminders.schedule(this)
        BalanceWidget.refresh(this)
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
fun mk(s: String) = if (Store.hideBal.value) "RM ••••" else s
fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))
fun periodLabel(c: Category) = when (c.period) {
    "DAILY" -> "Daily"; "CYCLE" -> "Salary cycle"; "WEEKLY" -> "Weekly"; "YEARLY" -> "Yearly"; else -> "Monthly · day ${c.startDay}"
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
    return if (h in 5..11) "Good morning" else if (h in 12..17) "Good afternoon" else if (h in 18..20) "Good evening" else "Good night"
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
fun BottomBar(page: Int, go: (Int) -> Unit, onAdd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavItem(Icons.Filled.Home, "Home", page == 0) { go(0) }
            NavItem(Icons.Filled.PieChart, "Budgets", page == 1) { go(1) }
            FloatingActionButton(onClick = onAdd, modifier = Modifier.size(58.dp), shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Filled.Add, "Add payment") }
            NavItem(Icons.Filled.Savings, "Salary", page == 2) { go(2) }
            NavItem(Icons.Filled.CreditCard, "Debt", page == 3) { go(3) }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val pager = rememberPagerState { 5 }
    var enabled by remember { mutableStateOf(listenerOn(ctx)) }
    var menu by remember { mutableStateOf(false) }
    var addT by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
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
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { say(if (runCatching { ctx.contentResolver.openOutputStream(it)!!.use { o -> o.write(Store.exportCsv().toByteArray()) } }.isSuccess) "CSV exported" else "Export failed") }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { enabled = listenerOn(ctx); Store.rollover() } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val haptic = LocalHapticFeedback.current
    var firstTab by remember { mutableStateOf(true) }
    LaunchedEffect(pager.settledPage) { if (firstTab) firstTab = false else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val go: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { BottomBar(pager.currentPage, go) { addT = true } }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Crossfade(pager.currentPage, label = "title") { p ->
                        Text(when (p) { 0 -> greeting(); 1 -> "Budgets"; 2 -> "Commitment"; 3 -> "Debt"; else -> "History" }, style = MaterialTheme.typography.headlineLarge)
                    }
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    Surface(onClick = { menu = true }, modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.MoreHoriz, "More options") }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("History") }, leadingIcon = { Icon(Icons.Filled.History, null) }, onClick = { menu = false; go(4) })
                        DropdownMenuItem(text = { Text("Backup data") }, leadingIcon = { Icon(Icons.Filled.Upload, null) }, onClick = { menu = false; bk.launch("TrackIt-backup.json") })
                        DropdownMenuItem(text = { Text("Restore data") }, leadingIcon = { Icon(Icons.Filled.Download, null) }, onClick = { menu = false; rs.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text("Export payments (CSV)") }, leadingIcon = { Icon(Icons.Filled.TableChart, null) }, onClick = { menu = false; csv.launch("TrackIt-payments.csv") })
                        DropdownMenuItem(text = { Text("Notification filters") }, leadingIcon = { Icon(Icons.Filled.FilterList, null) }, onClick = { menu = false; filters = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Due reminders") }, leadingIcon = { Icon(Icons.Filled.Alarm, null) }, trailingIcon = { if (Store.remindOn.value) Icon(Icons.Filled.Check, "On") }, onClick = { Store.setRemind(!Store.remindOn.value) })
                        HorizontalDivider()
                        listOf(Triple("SYSTEM", "Theme: System", Icons.Filled.SettingsBrightness), Triple("LIGHT", "Theme: Light", Icons.Filled.LightMode), Triple("DARK", "Theme: Dark", Icons.Filled.DarkMode)).forEach { (k, l, ic) ->
                            DropdownMenuItem(text = { Text(l) }, leadingIcon = { Icon(ic, null) }, trailingIcon = { if (Store.theme.value == k) Icon(Icons.Filled.Check, "Selected") }, onClick = { menu = false; Store.setTheme(k) })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
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
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, flingBehavior = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = 0.3f)) { page ->
                Box(Modifier.graphicsLayer {
                    val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    alpha = 1f - off * 0.5f; scaleX = 1f - off * 0.05f; scaleY = 1f - off * 0.05f
                }) {
                    when (page) {
                        0 -> Activity(del, { go(1) })
                        1 -> Budgets({ editing = it; catSheet = true }, { editing = null; catSheet = true })
                        2 -> Commitment()
                        3 -> DebtScreen()
                        else -> History()
                    }
                }
            }
        }
    }
    if (addT) AddTxnSheet { addT = false }
    if (filters) FiltersSheet { filters = false }
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
    var showAll by remember { mutableStateOf(false) }
    val week = remember { weekDays() }
    val now = System.currentTimeMillis()
    val today = week.firstOrNull { now - it in 0 until 86400000L } ?: week[0]
    val rows = Store.cats.map { it to Store.spent(it) }
    val monthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val unc = Store.txns.filter { it.category == "Uncategorized" && !it.billPay && it.time >= monthStart }.sumOf { it.amount }
    val cyc = Store.cycle.value
    val total = if (cyc != null) Store.cycleBought() else rows.sumOf { it.second } + unc
    val cycBase = if (cyc != null) Store.txns.filter { it.time >= cyc.start } else Store.txns.toList()
    val base = Store.txns.toList()
    val cleft = Store.cycleLeft()
    val dl = cyc?.takeIf { it.next > now }?.let { Math.ceil((it.next - now) / 86400000.0).toInt().coerceAtLeast(1) }
    val budgeted = rows.filter { it.first.budget > 0 }
    val bTotal = budgeted.sumOf { it.first.budget }; val bSpent = budgeted.sumOf { it.second }
    val shown by animateFloatAsState(total.toFloat(), tween(600), label = "total")
    val needs = base.count { it.category == "Uncategorized" && !it.billPay }
    val list0 = sel?.let { s -> base.filter { it.time >= s && it.time < s + 86400000L } } ?: base
    val groups = list0.groupBy { dayLabel(it.time) }
    val cards = rows.filter { it.second > 0 || it.first.budget > 0 }.sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { d -> DayChip(d, (sel ?: today) == d, Store.txns.any { it.time >= d && it.time < d + 86400000L }) { sel = if (sel == d) null else d } }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text(if (cyc != null) "Spent this cycle" else "Spent this period", style = MaterialTheme.typography.labelMedium)
                            Text(rm(shown.toDouble()), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp), maxLines = 1)
                        }
                        if (cyc != null) Column(horizontalAlignment = Alignment.End) {
                            Text("Safe to spend", style = MaterialTheme.typography.labelSmall)
                            Text(if (Store.hideBal.value) "RM ••••" else if (dl != null && cleft > 0) "${rm(cleft / dl)}/day" else rm(cleft), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (bTotal > 0) BudgetBar(bSpent, bTotal, color = MaterialTheme.colorScheme.onPrimaryContainer, track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("${cycBase.size} payments", Color.White.copy(alpha = 0.55f), MaterialTheme.colorScheme.onPrimaryContainer)
                        if (needs > 0) Pill("$needs need a category", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        if (bTotal > 0) Text(statusText(bSpent, bTotal) + " budget", style = MaterialTheme.typography.labelSmall)
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(if (sel == null) "Recent activity" else dayLabel(sel!!) + " · " + rm(list0.filter { !it.income }.sumOf { it.amount }))
                TextButton({ showAll = true }) { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Search & see all") }
            }
        }
        if (list0.isEmpty()) item {
            EmptyState(Icons.Filled.Contactless, if (sel == null) "No payments yet" else "No payments this day",
                if (sel == null) "Pay with QR or Paywave and it appears here automatically, or tap + to add one." else "Tap the selected day again to see everything.")
        }
        groups.forEach { (day, list) ->
            if (sel == null) item(key = "h$day") {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel(day)
                    Text(rm(list.filter { !it.income }.sumOf { it.amount }), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { t ->
                Box(Modifier.enter(list.indexOf(t)).animateItem().clip(MaterialTheme.shapes.large)) { TxnRow(t) { pick = t } }
            }
        }
    }
    pick?.let { EditTxnSheet(it, onDelete) { pick = null } }
    if (showAll) AllPaymentsSheet({ showAll = false }) { pick = it }
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
                    Text("Share of spending in each category this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    if (total <= 0) Text("Add payments to see your chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else SpendDonut(rows.map { it.first.name to it.second }.filter { it.second > 0 }, total)
                }
            }
        }
        item { TrendCard() }
        if (Store.cats.isEmpty()) item { EmptyState(Icons.Filled.PieChart, "No categories", "Create a category to group payments and set a budget.") }
        items(Store.cats.toList(), key = { it.name }) { c ->
            val v = Store.spent(c)
            Card(modifier = Modifier.fillMaxWidth().enter(Store.cats.indexOf(c) + 2).animateItem(), shape = MaterialTheme.shapes.large, onClick = { onEdit(c) },
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
                    val lk = Store.items.filter { it.catName == c.name }
                    if (lk.isNotEmpty()) Text("Commitments: " + lk.joinToString { it.name + if (it.paid) " ✓" else "" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
        if (Store.snaps.isEmpty() && Store.csnaps.isEmpty()) item { EmptyState(Icons.Filled.History, "Nothing here yet", "When a budget period ends, its summary is saved here automatically.") }
        items(Store.csnaps.toList(), key = { it.id }) { c -> CycleSnapCard(c) }
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
    OutlinedTextField(v, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = kb != KeyboardType.Text || label == "Name" || label == "Merchant" || label.startsWith("Merchant") || label.startsWith("Carry") || label == "Keyword" || label.startsWith("New group"),
        isError = err != null, supportingText = { if (err != null) Text(err) }, shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = kb))

@Composable
fun EditTxnSheet(t: Txn, onDelete: (Txn) -> Unit, close: () -> Unit) {
    var amt by remember(t.id) { mutableStateOf("%.2f".format(t.amount)) }
    var mer by remember(t.id) { mutableStateOf(t.merchant) }
    var cat by remember(t.id) { mutableStateOf(t.category) }
    var inc by remember(t.id) { mutableStateOf(t.income) }
    var cnt by remember(t.id) { mutableStateOf(t.counted) }
    var memo by remember(t.id) { mutableStateOf(t.note) }
    var splitOpen by remember(t.id) { mutableStateOf(false) }
    var later by remember(t.id) { mutableStateOf(t.later) }
    var months by remember(t.id) { mutableStateOf("1") }
    var fee by remember(t.id) { mutableStateOf("") }
    var paidN by remember(t.id) { mutableStateOf("0") }
    val v = amt.replace(",", ".").toDoubleOrNull()
    val sel = remember(t.id) { mutableStateMapOf<String, String>() }
    val linkedNow = Store.linksOf(t.id)
    val alloc = linkedNow.sumOf { it.amount } + sel.values.sumOf { it.replace(",", ".").toDoubleOrNull() ?: 0.0 }
    Sheet("Edit payment", close) {
        Field(amt, { amt = it }, "Amount RM", KeyboardType.Decimal, if (v == null || v <= 0) "Enter a valid amount" else null)
        Field(mer, { mer = it }, "Merchant")
        NoteField(memo) { memo = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("This is income (not counted as spending)", Modifier.weight(1f))
            Switch(inc, { inc = it })
        }
        if (inc) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Include in salary balance", Modifier.weight(1f))
            Switch(cnt, { cnt = it })
        }
        if (!inc) {
            SectionLabel("Category")
            Chips((Store.cats.map { it.name } + "Uncategorized").map { it to it }, cat) { cat = it }
            val hist = Store.history(mer)
            if (hist.isNotEmpty()) Text("Usually: " + hist.joinToString { "${it.first} (${it.second})" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.split.isNotBlank()) Text("Split: " + Store.parts(t).joinToString { "${it.first} ${rm(it.second)}" }, style = MaterialTheme.typography.bodySmall)
            TextButton({ splitOpen = true }, Modifier.fillMaxWidth()) { Text(if (t.split.isNotBlank()) "Edit split" else "Split across categories") }
        }
        if (t.later) Text("SPayLater purchase. Manage or delete the plan in the Debt tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!inc && !t.later && !t.billPay && Store.items.isNotEmpty()) {
            SectionLabel("Pays commitment")
            linkedNow.forEach { l ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text((Store.items.firstOrNull { it.id == l.itemId }?.name ?: "") + " · " + rm(l.amount), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ Store.unlink(l) }) { Text("Unlink") }
                }
            }
            Store.items.filter { !it.paid && it.amount - it.paidPart > 0.005 }.sortedBy { if (it.due == 0) 99 else it.due }.forEach { i ->
                val rem = i.amount - i.paidPart
                val toggle: () -> Unit = {
                    if (i.id in sel) { sel.remove(i.id) }
                    else { val free = ((v ?: t.amount) - alloc).coerceAtLeast(0.0); sel[i.id] = "%.2f".format(minOf(rem, free)) }
                }
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i.id in sel, { toggle() })
                    Column(Modifier.weight(1f)) {
                        Text(i.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Remaining ${rm(rem)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (i.id in sel) OutlinedTextField(sel[i.id] ?: "", { sel[i.id] = it }, Modifier.width(112.dp), singleLine = true,
                        shape = MaterialTheme.shapes.small, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
            if (linkedNow.isNotEmpty() || sel.isNotEmpty()) Text("Allocated ${rm(alloc)} of ${rm(v ?: t.amount)} · remaining ${rm((v ?: t.amount) - alloc)}", style = MaterialTheme.typography.bodySmall,
                color = if (alloc > (v ?: t.amount) + 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PrimaryButton("Save changes", v != null && v > 0 && alloc <= v + 0.005) {
            val u = t.copy(amount = v!!, merchant = mer.trim().ifBlank { t.merchant }, category = if (inc) "Income" else if (cat == "Income") "Uncategorized" else cat, income = inc, counted = cnt, split = if (cat != t.category || Math.abs(v!! - t.amount) > 0.004) "" else t.split, note = memo.trim())
            Store.updateTxn(t, u)
            Store.linkAll(u.id, sel.mapNotNull { (id, a) -> a.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it } })
            close()
        }
        TextButton({ close(); onDelete(t) }, Modifier.fillMaxWidth()) { Text("Delete payment", color = MaterialTheme.colorScheme.error) }
    }
    if (splitOpen) SplitSheet(t) { splitOpen = false; close() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxnSheet(close: () -> Unit) {
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Uncategorized") }
    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    var later by remember { mutableStateOf(false) }
    var months by remember { mutableStateOf("1") }
    var fee by remember { mutableStateOf("") }
    var paidN by remember { mutableStateOf("0") }
    var memo by remember { mutableStateOf("") }
    val v = amt.replace(",", ".").toDoubleOrNull()
    Sheet("Add payment", close) {
        Field(amt, { amt = it }, "Amount RM", KeyboardType.Decimal)
        Field(note, { note = it }, "Merchant")
        NoteField(memo) { memo = it }
        SectionLabel("Category")
        Chips((Store.cats.map { it.name } + "Uncategorized").map { it to it }, cat) { cat = it }
        OutlinedButton({ pickDate = true }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Event, null); Spacer(Modifier.width(8.dp)); Text(fmtDate(time)) }
        PrimaryButton("Save payment", v != null && v > 0) {
            val tx = Txn("manual:${System.nanoTime()}", v!!, note.trim().ifBlank { "Manual" }, cat, time, note = memo.trim())
            Store.addManual(tx)
            close()
        }
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
fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatSheet(edit: Category?, close: () -> Unit) {
    var n by remember { mutableStateOf(edit?.name ?: "") }
    val kws = remember { mutableStateListOf<String>().apply { addAll((edit?.keywords ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }) } }
    var kin by remember { mutableStateOf("") }
    var b by remember { mutableStateOf(edit?.budget?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "") }
    var p by remember { mutableStateOf(edit?.period ?: "MONTHLY") }
    var d by remember { mutableStateOf((edit?.startDay ?: 1).toString()) }
    val dup = Store.cats.any { it.name.equals(n.trim(), true) && it.name != edit?.name }
    fun addKw() {
        kin.split(",").map { it.trim() }.filter { w -> w.isNotEmpty() && kws.none { it.equals(w, true) } }.forEach { kws.add(it) }
        kin = ""
    }
    Sheet(if (edit == null) "New category" else "Edit category", close, true) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.padding(top = 8.dp)) { CatAvatar(n.ifBlank { "?" }, 52.dp) }
            OutlinedTextField(n, { n = it }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true, isError = dup,
                supportingText = { if (dup) Text("Name already used") }, shape = MaterialTheme.shapes.medium)
        }
        Group("Keywords") {
            Text("Payments from a merchant containing a keyword go into this category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (kws.isEmpty()) Text("No keywords yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                kws.toList().forEach { k ->
                    InputChip(selected = false, onClick = { kws.remove(k) }, label = { Text(k) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Remove $k", Modifier.size(16.dp)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(kin, { kin = it }, Modifier.weight(1f), label = { Text("Add keyword") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addKw() }))
                FilledTonalIconButton({ addKw() }, enabled = kin.isNotBlank()) { Icon(Icons.Filled.Add, "Add keyword") }
            }
        }
        Group("Budget") {
            Field(b, { b = it }, "Budget RM (optional)", KeyboardType.Decimal)
            Text("Resets", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly", "CYCLE" to "Salary cycle").forEach { (k, l) ->
                    FilterChip(selected = p == k, onClick = { p = k }, label = { Text(l) })
                }
            }
            AnimatedVisibility(p == "MONTHLY") { Field(d, { d = it.filter { c -> c.isDigit() }.take(2) }, "Reset on day of month (1-28)", KeyboardType.Number) }
        }
        PrimaryButton("Save category", n.isNotBlank() && !dup) {
            if (kin.isNotBlank()) addKw()
            val nc = Category(n.trim(), kws.joinToString(","), b.replace(",", ".").toDoubleOrNull() ?: 0.0, p, d.toIntOrNull()?.coerceIn(1, 28) ?: 1, edit?.periodStart ?: 0L)
            if (edit == null) Store.addCat(nc) else Store.editCat(edit, nc)
            close()
        }
        if (edit != null) TextButton({ Store.delCat(edit); close() }, Modifier.fillMaxWidth()) { Text("Delete category", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun TxnRow(t: Txn, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val check = !t.income && !t.billPay && t.category != "Uncategorized" && Store.uncertain(t.merchant)
    val date = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(t.time))
    val sub = when {
        t.billPay -> "SPayLater bill · $date"
        t.category == "Uncategorized" -> "Tap to categorize · $date"
        else -> "${t.category} · $date"
    }
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CatAvatar(t.category)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.merchant, Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.split.isNotBlank()) Icon(Icons.Filled.CallSplit, "Split", Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    if (check) Icon(Icons.Filled.Warning, "Check category", Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.error)
                    if (t.later) Icon(Icons.Filled.CreditCard, "SPayLater", Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    if (t.billPay) Icon(Icons.Filled.Payments, "SPayLater bill", Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    if (t.note.isNotBlank()) Icon(Icons.Filled.Description, "Has note", Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (t.category == "Uncategorized" && !t.billPay && !t.income) MaterialTheme.colorScheme.tertiary else muted,
                    fontWeight = if (t.category == "Uncategorized" && !t.billPay && !t.income) FontWeight.SemiBold else FontWeight.Normal)
            }
            Text((if (t.income) "+" else "") + rm(t.amount), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                color = if (t.income) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun AllPaymentsSheet(onClose: () -> Unit, onPick: (Txn) -> Unit) {
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    var off by remember { mutableIntStateOf(0) }
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MONTH, -off) }
    val s = cal.timeInMillis
    val e = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
    val list = Store.txns.filter { it.time in s until e && (cat == "All" || it.category == cat) && (q.isBlank() || it.merchant.contains(q.trim(), true) || it.note.contains(q.trim(), true)) }
    Sheet("All payments", onClose, true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ off++ }) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
            Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(s)), style = MaterialTheme.typography.titleMedium)
            IconButton({ if (off > 0) off-- }, enabled = off > 0) { Icon(Icons.Filled.ChevronRight, "Next month") }
        }
        Field(q, { q = it }, "Merchant or note")
        Chips((listOf("All") + Store.cats.map { it.name } + listOf("Uncategorized", "Income")).map { it to it }, cat) { cat = it }
        Text("${rm(list.filter { !it.income }.sumOf { it.amount })} spent · ${rm(list.filter { it.income }.sumOf { it.amount })} in · ${list.size} payments",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (list.isEmpty()) Text("No payments match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { t -> TxnRow(t) { onPick(t) } }
    }
}

fun trend(monthly: Boolean): List<Pair<String, Double>> {
    val fmt = SimpleDateFormat(if (monthly) "MMM" else "EEE", Locale.getDefault())
    return (if (monthly) 5 downTo 0 else 6 downTo 0).map { i ->
        val s = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (monthly) { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -i) } else add(Calendar.DAY_OF_MONTH, -i)
        }
        val e = (s.clone() as Calendar).apply { if (monthly) add(Calendar.MONTH, 1) else add(Calendar.DAY_OF_MONTH, 1) }
        fmt.format(s.time) to Store.txns.filter { !it.income && !it.billPay && it.time >= s.timeInMillis && it.time < e.timeInMillis }.sumOf { it.amount }
    }
}

@Composable
fun TrendCard() {
    var monthly by remember { mutableStateOf(false) }
    val data = trend(monthly)
    val max = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Spending trend", style = MaterialTheme.typography.titleMedium)
                    Text(if (monthly) "Last 6 months" else "Last 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Chips(listOf("D" to "Daily", "M" to "Monthly"), if (monthly) "M" else "D") { monthly = it == "M" }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                data.forEachIndexed { i, (l, v) -> TrendBar(l, (v / max).toFloat(), i == data.lastIndex) }
            }
            val cur = data.last().second
            val prev = data[data.size - 2].second
            Text(
                if (monthly && prev > 0) "This month ${rm(cur)} · ${Math.abs(((cur - prev) / prev * 100).toInt())}% ${if (cur >= prev) "more" else "less"} than last month"
                else if (monthly) "This month ${rm(cur)}" else "Today ${rm(cur)} · yesterday ${rm(prev)}",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun CycleSnapCard(c: CSnap) {
    val left = c.salary + c.carryIn + c.income - c.committed - c.spent
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Savings, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("Salary cycle", style = MaterialTheme.typography.titleMedium)
                    Text(fmtDate(c.start) + " – " + fmtDate(c.end - 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(if (left >= 0) "Saved ${rm(left)}" else "Over ${rm(-left)}",
                    if (left >= 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    if (left >= 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer)
            }
            BudgetBar(c.committed + c.spent, c.salary + c.carryIn + c.income)
            if (c.carryIn != 0.0) Text("Carried in " + (if (c.carryIn > 0) "+" else "−") + rm(Math.abs(c.carryIn)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.carryOut != 0.0) Text("Carried to next cycle " + (if (c.carryOut > 0) "+" else "−") + rm(Math.abs(c.carryOut)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Salary ${rm(c.salary)}" + (if (c.income > 0) " + ${rm(c.income)} income" else "") + " · spent ${rm(c.spent)} · unpaid commitments ${rm(c.committed)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun todayStart(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Modifier.enter(i: Int): Modifier {
    if (i > 8) return this
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(i * 45L); go = true }
    val a by animateFloatAsState(if (go) 1f else 0f, tween(380), label = "in")
    return this.graphicsLayer { alpha = a; translationY = (1f - a) * 36f }
}

@Composable
fun SpendDonut(data: List<Pair<String, Double>>, total: Double) {
    val big = data.filter { it.second / total >= 0.03 }.sortedByDescending { it.second }
    val rest = data.filter { it.second / total < 0.03 }.sumOf { it.second }
    val slices = big.map { Triple(it.first, it.second, catColor(it.first)) } + (if (rest > 0) listOf(Triple("Others", rest, Color.Gray)) else emptyList())
    var sel by remember { mutableStateOf(-1) }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val prog by animateFloatAsState(if (go) 1f else 0f, tween(900), label = "donut")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(20.dp)) {
                drawArc(track, 0f, 360f, false, style = Stroke(30.dp.toPx()))
                var start = -90f
                slices.forEachIndexed { i, (_, v, c) ->
                    val sweep = (v / total * 360.0).toFloat() * prog
                    drawArc(c, start, (sweep - 2f).coerceAtLeast(0.5f), false, alpha = if (sel == -1 || sel == i) 1f else 0.35f,
                        style = Stroke(if (i == sel) 40.dp.toPx() else 30.dp.toPx()))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (sel in slices.indices) slices[sel].first else "Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rm(if (sel in slices.indices) slices[sel].second else total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        slices.forEachIndexed { i, (n, v, c) ->
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { sel = if (sel == i) -1 else i }.padding(vertical = 8.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(10.dp))
                Text(n, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel == i) FontWeight.Bold else FontWeight.Normal)
                Text("${(v / total * 100).toInt()}%", Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rm(v), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun Commitment() {
    val cyc = Store.cycle.value
    var sheet by remember { mutableStateOf<String?>(null) }
    var editItem by remember { mutableStateOf<Item?>(null) }
    var addItem by remember { mutableStateOf(false) }
    var billSheet by remember { mutableStateOf(false) }
    val bill = Store.bill()
    val safe = Store.cycleLeft(); val bal = Store.cashLeft(); val inc = Store.cycleIncome()
    val spent = Store.spentAll(); val unpaid = Store.unpaid()
    val balShown by animateFloatAsState(bal.toFloat(), tween(700), label = "bal")
    val haptic = LocalHapticFeedback.current
    val now = System.currentTimeMillis()
    val dl = cyc?.takeIf { it.next > now }?.let { Math.ceil((it.next - now) / 86400000.0).toInt().coerceAtLeast(1) }
    val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val dark = ButtonDefaults.buttonColors(containerColor = ink, contentColor = MaterialTheme.colorScheme.primaryContainer)
    val sorted = Store.items.sortedWith(compareBy({ it.paid }, { if (it.due == 0) 99 else it.due }))
    val paidAmt = Store.items.sumOf { if (it.paid) it.amount else it.paidPart }
    val allAmt = Store.items.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = ink)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (cyc == null) {
                        Text("Start your salary cycle", style = MaterialTheme.typography.headlineSmall)
                        Text("Enter your salary and the date it arrived, then pick the commitments you need to pay this cycle.")
                        Button({ sheet = "new" }, Modifier.fillMaxWidth().height(54.dp), colors = dark) { Text("Start first cycle", fontWeight = FontWeight.SemiBold) }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Balance", style = MaterialTheme.typography.titleMedium)
                                Text("Cycle since ${fmtDate(cyc.start)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(mk(rm(balShown.toDouble())), Modifier.clip(MaterialTheme.shapes.small).clickable { Store.setHide(!Store.hideBal.value) }, style = MaterialTheme.typography.displayMedium.copy(fontSize = 42.sp), maxLines = 1)
                        Pill(if (Store.hideBal.value) "Amounts hidden" else "Safe to spend ${rm(safe)} after unpaid",
                            if (safe < 0) MaterialTheme.colorScheme.errorContainer else Color.White.copy(alpha = 0.55f),
                            if (safe < 0) MaterialTheme.colorScheme.onErrorContainer else ink)
                        val tot = (cyc.salary + cyc.carry + inc).coerceAtLeast(1.0)
                        val sp = (spent / tot).toFloat().coerceAtLeast(0f); val up = (unpaid / tot).toFloat().coerceAtLeast(0f); val sf = (safe / tot).toFloat().coerceAtLeast(0f)
                        val sum = (sp + up + sf).coerceAtLeast(1f)
                        Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
                            Box(Modifier.weight((sp / sum).coerceAtLeast(0.001f)).fillMaxHeight().background(ink))
                            Box(Modifier.weight((up / sum).coerceAtLeast(0.001f)).fillMaxHeight().background(ink.copy(alpha = 0.4f)))
                            Box(Modifier.weight((sf / sum).coerceAtLeast(0.001f)).fillMaxHeight().background(Color.White.copy(alpha = 0.75f)))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendDot(ink, "Spent & paid"); LegendDot(ink.copy(alpha = 0.4f), "Unpaid"); LegendDot(Color.White.copy(alpha = 0.75f), "Safe")
                        }
                        Surface(color = Color.White.copy(alpha = 0.45f), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Stat("Salary", mk(rm(cyc.salary))); if (cyc.carry != 0.0) Stat("Carried forward", mk((if (cyc.carry > 0) "+" else "−") + rm(Math.abs(cyc.carry)))); Stat("Income", mk("+" + rm(inc))); Stat("Spent & paid", mk(rm(spent)))
                            }
                        }
                        Text(if (Store.hideBal.value) "Next salary: " + (if (cyc.next > 0) fmtDate(cyc.next) else "not set")
                            else if (dl != null && safe > 0) "About ${rm(safe / dl)} per day for $dl days until ${fmtDate(cyc.next)}"
                            else if (cyc.next > 0) "Next salary: ${fmtDate(cyc.next)}" else "Set the next salary date to see a daily limit",
                            style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button({ sheet = "new" }, colors = dark) { Text("Start new cycle") }
                            TextButton({ sheet = "edit" }) { Text("Edit salary", color = ink) }
                            Spacer(Modifier.weight(1f))
                            IconButton({ Store.setHide(!Store.hideBal.value) }) {
                                Icon(if (Store.hideBal.value) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (Store.hideBal.value) "Show amounts" else "Hide amounts")
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Commitments", style = MaterialTheme.typography.titleMedium)
                            Text("${Store.items.count { it.paid }} of ${Store.items.size} paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton({ addItem = true }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add") }
                    }
                    if (allAmt > 0) {
                        BudgetBar(paidAmt, allAmt, color = MaterialTheme.colorScheme.tertiary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Paid ${rm(paidAmt)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Unpaid ${rm(unpaid)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (bill != null || Store.dlogs.any { it.itemId.startsWith("bill") }) item { BillCard(bill) { billSheet = true } }
        if (Store.items.isEmpty() && bill == null) item { EmptyState(Icons.Filled.Event, "No commitments", "Add rent, loans or bills you need to pay this cycle. Saved ones can be picked again next cycle.") }
        items(sorted, key = { it.id }) { i ->
            val soon = !i.paid && i.due > 0 && (i.due - today) in 0..3
            Card(modifier = Modifier.fillMaxWidth().alpha(if (i.paid) 0.65f else 1f).enter(sorted.indexOf(i) + 2).animateItem(), shape = MaterialTheme.shapes.large, onClick = { editItem = i },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i.paid, { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); Store.togglePaid(i) })
                    Column(Modifier.weight(1f)) {
                        Text(i.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((if (i.paid) "Paid" else if (i.paidPart > 0.005) "Partly paid ${rm(i.paidPart)}" else if (i.due > 0) "Due on day ${i.due}" else "Unpaid") + (if (i.catName.isNotBlank()) " · ${i.catName}" else "") +
                            (Store.debtLabel(i.debtId)?.let { " · Debt: $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = if (i.paid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(rm(if (i.paid) i.amount else i.amount - i.paidPart), style = MaterialTheme.typography.titleMedium)
                        if (soon) Pill("Due soon", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
    }
    sheet?.let { CycleSheet(it == "new") { sheet = null } }
    if (addItem) ItemSheet(null) { addItem = false }
    editItem?.let { ItemSheet(it) { editItem = null } }
    if (billSheet) BillSheet { billSheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(label: String, value: Long, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton({ open = true }, Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Filled.Event, null); Spacer(Modifier.width(8.dp)); Text("$label: " + if (value > 0) fmtDate(value) else "not set")
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = if (value > 0) value else System.currentTimeMillis())
        DatePickerDialog({ open = false }, confirmButton = {
            TextButton({
                st.selectedDateMillis?.let { ms ->
                    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                    onPick(Calendar.getInstance().apply { clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH)) }.timeInMillis)
                }
                open = false
            }) { Text("OK") }
        }) { DatePicker(st) }
    }
}

@Composable
fun CycleSheet(newCycle: Boolean, close: () -> Unit) {
    val cyc = Store.cycle.value
    var sal by remember { mutableStateOf(cyc?.salary?.let { "%.2f".format(it) } ?: "") }
    var recv by remember { mutableLongStateOf(todayStart()) }
    var next by remember { mutableLongStateOf(cyc?.next ?: 0L) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(Store.tpls.map { it.id }) } }
    val v = sal.replace(",", ".").toDoubleOrNull()
    val prev = Store.cashLeft()
    var carryOn by remember { mutableStateOf(true) }
    var carryTxt by remember { mutableStateOf(if (newCycle) "%.2f".format(prev) else "%.2f".format(cyc?.carry ?: 0.0)) }
    val cv = carryTxt.replace(",", ".").toDoubleOrNull()
    val needCarry = (newCycle && cyc != null && carryOn) || !newCycle
    Sheet(if (newCycle) "Start new cycle" else "Edit salary", close) {
        Field(sal, { sal = it }, "Salary RM", KeyboardType.Decimal)
        if (newCycle && cyc != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Carry forward previous balance")
                Text("Previous balance ${rm(prev)}" + if (prev < 0) " (overspent, will be deducted)" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(carryOn, { carryOn = it })
        }
        if (needCarry) Field(carryTxt, { carryTxt = it }, "Carry forward amount RM")
        if (newCycle) DateButton("Salary received", recv) { recv = it }
        DateButton("Next salary (estimate)", next) { next = it }
        if (next > 0) TextButton({ next = 0L }) { Text("Clear next salary date") }
        if (newCycle) {
            SectionLabel("Commitments to pay this cycle")
            if (Store.tpls.isEmpty()) Text("No saved commitments yet. Add them after starting the cycle.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Store.tpls.forEach { t ->
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { if (t.id in chosen) chosen.remove(t.id) else chosen.add(t.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(t.id in chosen, { if (it) chosen.add(t.id) else chosen.remove(t.id) })
                    Text(t.name, Modifier.weight(1f)); Text(rm(t.amount))
                }
            }
        }
        PrimaryButton(if (newCycle) "Start cycle" else "Save", v != null && v > 0 && (!needCarry || cv != null)) {
            val cr = if (needCarry) (cv ?: 0.0) else 0.0
            if (newCycle) Store.startCycle(v!!, recv, next, Store.tpls.filter { it.id in chosen }, cr) else Store.editCycle(v!!, next, cr)
            close()
        }
    }
}

@Composable
fun ItemSheet(old: Item?, close: () -> Unit) {
    var n by remember { mutableStateOf(old?.name ?: "") }
    var a by remember { mutableStateOf(old?.amount?.let { "%.2f".format(it) } ?: "") }
    var d by remember { mutableStateOf(old?.due?.takeIf { it > 0 }?.toString() ?: "") }
    var keep by remember { mutableStateOf(true) }
    var cat by remember { mutableStateOf(old?.catName ?: "") }
    var debt by remember { mutableStateOf(old?.debtId ?: "") }
    var gsel by remember { mutableStateOf(old?.debtId?.let { id -> if (id.startsWith("g:")) id.removePrefix("g:") else (Store.debts.firstOrNull { it.id == id }?.group ?: "") } ?: "") }
    var moreCats by remember { mutableStateOf(false) }
    val v = a.replace(",", ".").toDoubleOrNull()
    Sheet(if (old == null) "Add commitment" else "Edit commitment", close) {
        Field(n, { n = it }, "Name")
        Field(a, { a = it }, "Amount RM", KeyboardType.Decimal)
        Field(d, { d = it.filter { c -> c.isDigit() }.take(2) }, "Due day of month (optional)", KeyboardType.Number)
        SectionLabel("Linked category")
        val catOrder = Store.cats.map { it.name }.sortedByDescending { c -> Store.txns.count { it.category == c } }
        val shownCats = if (moreCats) catOrder else catOrder.take(6) + (if (cat.isNotBlank() && cat !in catOrder.take(6)) listOf(cat) else emptyList())
        Chips(listOf("" to "None") + shownCats.map { it to it } + (if (!moreCats && catOrder.size > 6) listOf("__more" to "More…") else emptyList()), cat) { if (it == "__more") moreCats = true else cat = it }
        if (Store.debts.isNotEmpty()) {
            SectionLabel("Linked debt (balance drops when paid)")
            val groupNames = Store.debts.map { it.group }.filter { it.isNotBlank() }.distinct()
            val firstRow = listOf("" to "None") + Store.debts.filter { it.group.isBlank() }.map { it.id to it.name } + groupNames.map { "grp:$it" to it }
            Chips(firstRow, if (gsel.isNotBlank()) "grp:$gsel" else debt) { k ->
                if (k.startsWith("grp:")) { gsel = k.removePrefix("grp:"); debt = if (gsel == "SPayLater") "" else "g:$gsel" } else { gsel = ""; debt = k }
            }
            if (gsel.isNotBlank()) {
                SectionLabel(if (gsel == "SPayLater") "Which purchase?" else "Pay which part?")
                Chips((if (gsel != "SPayLater") listOf("g:$gsel" to "Whole group") else emptyList()) + Store.debts.filter { it.group == gsel }.map { it.id to it.name.removePrefix("$gsel · ") }, debt) { debt = it }
            }
        }
        if (old == null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Save to my list for future cycles", Modifier.weight(1f)); Switch(keep, { keep = it })
        }
        PrimaryButton("Save", n.isNotBlank() && v != null && v > 0 && !(gsel == "SPayLater" && debt.isBlank())) {
            Store.saveItem(old, Item(old?.id ?: "i${System.nanoTime()}", n.trim(), v!!, d.toIntOrNull()?.coerceIn(0, 31) ?: 0, old?.paid ?: false, old?.txnId ?: "", cat, debt, old?.paidPart ?: 0.0), keep)
            close()
        }
        if (old != null) {
            TextButton({ Store.delItem(old, false); close() }, Modifier.fillMaxWidth()) { Text("Remove from this cycle", color = MaterialTheme.colorScheme.error) }
            if (Store.tpls.any { it.id == old.id }) TextButton({ Store.delItem(old, true); close() }, Modifier.fillMaxWidth()) { Text("Remove from my list too", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun DebtScreen() {
    var sheet by remember { mutableStateOf<Debt?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addPl by remember { mutableStateOf(false) }
    var presetGrp by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(setOf<String>()) }
    val total = Store.debts.sumOf { it.balance }
    val orig = Store.debts.sumOf { it.total }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total debt remaining", style = MaterialTheme.typography.labelLarge)
                    Text(mk(rm(total)), style = MaterialTheme.typography.displayMedium)
                    val fin = Store.debts.mapNotNull { Store.payoff(it) }.maxByOrNull { it.second }
                    val mon = Store.debts.filter { it.balance > 0.005 }.sumOf { it.monthly }
                    if (fin != null) Text("Debt-free by ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(fin.second))} · ${mk(rm(mon))}/month", style = MaterialTheme.typography.titleSmall)
                    if (orig > 0) {
                        BudgetBar(orig - total, orig, color = MaterialTheme.colorScheme.onPrimaryContainer, track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        Text("${((orig - total) / orig * 100).toInt()}% paid off of ${mk(rm(orig))}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (Store.debts.isEmpty()) item { EmptyState(Icons.Filled.CreditCard, "No debts", "Add loans or cards, then link a commitment so the balance drops when you pay it.") }
        Store.debts.filter { it.group.isNotBlank() }.groupBy { it.group }.forEach { (g, list) ->
            item(key = "grp$g") {
                DebtGroupCard(g, list, g in open, { open = if (g in open) open - g else open + g }, { sheet = it }) {
                    if (g == "SPayLater") addPl = true else { presetGrp = g; adding = true }
                }
            }
        }
        items(Store.debts.filter { it.group.isBlank() }, key = { it.id }) { d ->
            Card(modifier = Modifier.fillMaxWidth().enter(Store.debts.indexOf(d) + 1).animateItem(), shape = MaterialTheme.shapes.large, onClick = { sheet = d },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CatAvatar(d.name)
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text((if (d.monthly > 0) "${rm(d.monthly)}/month" else "No monthly set") + (if (d.due > 0) " · due day ${d.due}" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BudgetBar(d.total - d.balance, d.total, color = MaterialTheme.colorScheme.tertiary)
                    Text("${mk(rm(d.balance))} left of ${mk(rm(d.total))}", style = MaterialTheme.typography.bodyMedium)
                    val nd = Store.nextDue(d)
                    Store.payoff(d)?.let { Text("About ${it.first} month${if (it.first > 1) "s" else ""} left · done by ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(it.second))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (nd > 0 && d.balance > 0.005) Text("Next payment ${rm(d.monthly)} by ${fmtDate(nd)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Add SPayLater purchase") { addPl = true }
                OutlinedButton({ adding = true }, Modifier.fillMaxWidth().height(54.dp)) { Text("Add other debt") }
            }
        }
    }
    if (adding) DebtSheet(null, presetGrp) { adding = false; presetGrp = "" }
    if (addPl) PlanSheet { addPl = false }
    sheet?.let { DebtSheet(it) { sheet = null } }
}

@Composable
fun DebtSheet(old: Debt?, presetGroup: String = "", close: () -> Unit) {
    var n by remember { mutableStateOf(old?.name ?: "") }
    var t by remember { mutableStateOf(old?.total?.let { "%.2f".format(it) } ?: "") }
    var b by remember { mutableStateOf(old?.balance?.let { "%.2f".format(it) } ?: "") }
    var mo by remember { mutableStateOf(old?.monthly?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "") }
    var d by remember { mutableStateOf(old?.due?.takeIf { it > 0 }?.toString() ?: "") }
    var grp by remember { mutableStateOf(old?.group ?: presetGroup) }
    var newGrp by remember { mutableStateOf("") }
    val gnames = Store.debts.map { it.group }.filter { it.isNotBlank() }.distinct()
    val isPlan = old != null && old.months > 0
    var pc by remember { mutableStateOf(if (isPlan && old!!.monthly > 0) ((old.total - old.balance) / old.monthly + 0.0001).toInt().toString() else "0") }
    val tv = t.replace(",", ".").toDoubleOrNull()
    val mv = mo.replace(",", ".").toDoubleOrNull() ?: 0.0
    val bv = if (b.isBlank()) tv else b.replace(",", ".").toDoubleOrNull()
    val bv2 = if (isPlan) tv?.let { if ((pc.toIntOrNull() ?: 0) >= (old?.months ?: 0)) 0.0 else (it - (pc.toIntOrNull() ?: 0) * mv).coerceAtLeast(0.0) } else bv
    Sheet(if (old == null) "Add debt" else "Edit debt", close) {
        Field(n, { n = it }, "Name")
        Field(t, { t = it }, "Total debt RM", KeyboardType.Decimal)
        if (isPlan) Field(pc, { pc = it.filter { c -> c.isDigit() }.take(2) }, "Instalments paid (of ${old?.months})", KeyboardType.Number)
        else Field(b, { b = it }, "Current balance RM (blank = total)", KeyboardType.Decimal)
        Field(mo, { mo = it }, "Monthly payment RM (optional)", KeyboardType.Decimal)
        Field(d, { d = it.filter { c -> c.isDigit() }.take(2) }, "Due day of month (optional)", KeyboardType.Number)
        SectionLabel("Group (optional)")
        Chips(listOf("" to "None") + gnames.map { it to it }, grp) { grp = it; newGrp = "" }
        Field(newGrp, { newGrp = it }, "New group name")
        PrimaryButton("Save", n.isNotBlank() && tv != null && tv > 0 && bv2 != null) {
            Store.saveDebt(old, Debt(old?.id ?: "d${System.nanoTime()}", n.trim(), tv!!, bv2!!, mo.replace(",", ".").toDoubleOrNull() ?: 0.0, d.toIntOrNull()?.coerceIn(0, 31) ?: 0, old?.firstDue ?: 0L, old?.months ?: 0, newGrp.trim().ifBlank { grp }))
            close()
        }
        if (old != null) {
            val linked = Store.items.filter { it.debtId == old.id || (old.group.isNotBlank() && it.debtId == "g:" + old.group) }
            if (linked.isNotEmpty()) {
                SectionLabel("Linked commitments")
                linked.forEach { Text("${it.name} · ${rm(it.amount)}" + if (it.paid) " · paid" else "") }
            }
            val logs = Store.dlogs.filter { it.debtId == old.id }.take(8)
            if (logs.isNotEmpty()) {
                SectionLabel("Payment history")
                logs.forEach { l ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmtDate(l.time)} · −${rm(l.amount)}", Modifier.weight(1f))
                        if (l.itemId.startsWith("bill")) TextButton({ Store.undoPayment(l) }) { Text("Undo") }
                    }
                }
            }
            TextButton({ Store.delDebt(old); close() }, Modifier.fillMaxWidth()) { Text("Delete debt", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun LaterOptions(on: Boolean, onOn: (Boolean) -> Unit, months: String, onMonths: (String) -> Unit, fee: String, onFee: (String) -> Unit, paid: String, onPaid: (String) -> Unit, showPlan: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Paid with SPayLater")
            Text("Counts as spending, but is only deducted from your balance when the bill is paid.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onOn)
    }
    if (showPlan) {
        SectionLabel("Payment plan (due end of this month if bought by the 20th, otherwise end of next month)")
        Chips(listOf("1" to "Next month", "3" to "3 months", "6" to "6 months", "12" to "12 months"), months, onMonths)
        Field(fee, onFee, "Fee % per month (optional)", KeyboardType.Decimal)
        Field(paid, { onPaid(it.filter { c -> c.isDigit() }.take(2)) }, "Instalments already paid (0 if new)", KeyboardType.Number)
    }
}

@Composable
fun BillCard(b: Store.Bill?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CreditCard, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text("SPayLater bill", style = MaterialTheme.typography.titleMedium)
                Text(if (b == null) "No bill due · tap to view payments"
                    else "${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(b.month))} · ${b.parts.size} purchase${if (b.parts.size > 1) "s" else ""} · due ${fmtDate(b.month)}" + (if (Store.billReserved(b)) "" else " · not reserved yet"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (b == null) "Paid" else rm(b.total), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun BillSheet(close: () -> Unit) {
    val b = Store.bill()
    val logs = Store.dlogs.filter { l -> l.itemId.startsWith("bill") && Store.debts.any { it.id == l.debtId } }.take(12)
    if (b == null && logs.isEmpty()) { LaunchedEffect(Unit) { close() }; return }
    var amt by remember(b?.total) { mutableStateOf(b?.let { "%.2f".format(it.total) } ?: "") }
    var link by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    val v = amt.replace(",", ".").toDoubleOrNull()
    val cands = Store.txns.filter { !it.income && !it.later && !it.billPay && it.time > System.currentTimeMillis() - 45L * 86400000L }.take(15)
    Sheet("SPayLater bill", close) {
        if (b != null) {
            Text("Due ${fmtDate(b.month)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.parts.forEach { (d, due) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(d.name.removePrefix("SPayLater · "), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(rm(due), fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider()
            Chips(listOf("N" to "New payment", "L" to "Already recorded"), if (link) "L" else "N") { link = it == "L"; pick = null }
            if (link) {
                Text("Use this if the bill was already paid and the payment is in your list. It is counted once in your balance and removed from category spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (cands.isEmpty()) Text("No recent payments found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                cands.forEach { t ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { pick = t.id; amt = "%.2f".format(minOf(t.amount, b.total)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(pick == t.id, { pick = t.id; amt = "%.2f".format(minOf(t.amount, b.total)) })
                        Column(Modifier.weight(1f)) {
                            Text(t.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmtDate(t.time)} · ${rm(t.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Field(amt, { amt = it }, "Amount to pay RM", KeyboardType.Decimal)
            Text("Paying less than the total is a partial payment. The remainder stays due.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton("Pay ${rm(v ?: 0.0)}", v != null && v > 0 && v <= b.total + 0.005 && (!link || pick != null)) { Store.payBill(v!!, pick); close() }
        } else Text("No bill due right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (logs.isNotEmpty()) {
            HorizontalDivider()
            SectionLabel("Payment history")
            logs.forEach { l ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Store.debts.firstOrNull { it.id == l.debtId }?.name?.removePrefix("SPayLater · ") ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmtDate(l.time)} · ${rm(l.amount)}" + if (l.itemId.startsWith("billtx:")) " · linked payment" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ Store.undoPayment(l) }) { Text("Undo") }
                }
            }
        }
    }
}

@Composable
fun PlanSheet(close: () -> Unit) {
    val cyc = Store.cycle.value
    var n by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("6") }
    var fee by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(todayStart()) }
    var paid by remember { mutableStateOf("0") }
    var cat by remember { mutableStateOf("") }
    var count by remember { mutableStateOf(true) }
    val pv = p.replace(",", ".").toDoubleOrNull()
    val inCycle = cyc == null || date >= cyc.start
    Sheet("Add SPayLater", close) {
        Field(n, { n = it }, "Name")
        Field(p, { p = it }, "Price RM", KeyboardType.Decimal)
        SectionLabel("Plan")
        Chips(listOf("1" to "Next month", "3" to "3 months", "6" to "6 months", "12" to "12 months"), months) { months = it }
        Field(fee, { fee = it }, "Fee % per month (optional)", KeyboardType.Decimal)
        DateButton("Purchase date", date) { date = it }
        val mn = months.toIntOrNull() ?: 1
        if (pv != null && pv > 0) {
            val tot = Math.round(pv * (1 + (fee.replace(",", ".").toDoubleOrNull() ?: 0.0) / 100.0 * mn) * 100) / 100.0
            val base = Math.floor(tot / mn * 100) / 100.0
            Text(if (mn == 1) "Bill: ${rm(tot)}" else "${mn - 1} × ${rm(base)} + last ${rm(tot - base * (mn - 1))} (total ${rm(tot)})", fontWeight = FontWeight.SemiBold)
        }
        Text("Bought by the 20th: first bill is due on the 1st of next month. After the 20th: the 1st of the month after.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(paid, { paid = it.filter { c -> c.isDigit() }.take(2) }, "Instalments already paid", KeyboardType.Number)
        SectionLabel("Category")
        Chips(listOf("" to "None") + Store.cats.map { it.name to it.name }, cat) { cat = it }
        if (inCycle) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Count as spending in this cycle", Modifier.weight(1f)); Switch(count, { count = it })
        } else Text("Bought before this cycle, so it will not count as spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("Add SPayLater", n.isNotBlank() && pv != null && pv > 0) {
            Store.addPlan(n.trim(), pv!!, months.toIntOrNull() ?: 1, fee.replace(",", ".").toDoubleOrNull() ?: 0.0, date, paid.toIntOrNull() ?: 0, cat.ifBlank { "Uncategorized" }, count && inCycle)
            close()
        }
    }
}

@Composable
fun SplitSheet(t: Txn, close: () -> Unit) {
    val rows = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            if (t.split.isNotBlank()) Store.parts(t).forEach { add(it.first to "%.2f".format(it.second)) }
            else { add(t.category to "%.2f".format(t.amount)); add("" to "") }
        }
    }
    val sum = rows.sumOf { it.second.replace(",", ".").toDoubleOrNull() ?: 0.0 }
    val ok = rows.size >= 2 && rows.all { it.first.isNotBlank() && (it.second.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0 } && Math.abs(sum - t.amount) < 0.005
    Sheet("Split payment", close) {
        Text("Total ${rm(t.amount)} · allocated ${rm(sum)} · remaining ${rm(t.amount - sum)}", color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed { i, (c, a) ->
            Chips((Store.cats.map { it.name } + "Uncategorized").map { it to it }, c) { rows[i] = it to a }
            Field(a, { rows[i] = c to it }, "Amount RM", KeyboardType.Decimal)
        }
        Row {
            TextButton({ rows.add("" to "") }) { Text("Add part") }
            if (rows.size > 2) TextButton({ rows.removeAt(rows.lastIndex) }) { Text("Remove last") }
        }
        PrimaryButton("Save split", ok) { Store.setSplit(t, rows.map { it.first to it.second.replace(",", ".").toDouble() }); close() }
        if (t.split.isNotBlank()) TextButton({ Store.setSplit(t, emptyList()); close() }, Modifier.fillMaxWidth()) { Text("Remove split", color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(close: () -> Unit) {
    var mode by remember { mutableStateOf("IGNORE") }
    var app by remember { mutableStateOf("") }
    var kw by remember { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Sheet("Notification filters", close, true) {
        Text("Control which payment notifications are recorded.", color = muted)
        Group("Rules") {
            if (Store.rules.isEmpty()) Text("No rules yet", color = muted)
            Store.rules.toList().forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("\"${r.keyword}\"", fontWeight = FontWeight.SemiBold)
                        Text((if (r.mode == "IGNORE") "Ignore" else "Only record if matched") + " · " + (if (r.pkg.isBlank()) "All apps" else Store.appLabel(r.pkg)), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    IconButton({ Store.delRule(r) }) { Icon(Icons.Filled.Delete, "Delete rule") }
                }
            }
        }
        Group("Add rule") {
            Chips(listOf("IGNORE" to "Ignore", "ONLY" to "Only record if"), mode) { mode = it }
            Text(if (mode == "IGNORE") "Notifications containing this word are not recorded." else "From the chosen app, only notifications containing this word are recorded.", style = MaterialTheme.typography.bodySmall, color = muted)
            Text("App", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = app == "", onClick = { app = "" }, label = { Text("All apps") })
                Store.seen.take(14).forEach { p -> FilterChip(selected = app == p, onClick = { app = p }, label = { Text(Store.appLabel(p)) }) }
            }
            Field(kw, { kw = it }, "Keyword")
            PrimaryButton("Add rule", kw.isNotBlank() && (mode == "IGNORE" || app.isNotBlank())) { Store.addRule(Rule("r${System.nanoTime()}", app, kw.trim(), mode)); kw = "" }
        }
        Group("Recently ignored") {
            if (Store.ignored.isEmpty()) Text("Nothing ignored yet", color = muted)
            Store.ignored.take(10).forEach { g ->
                Column {
                    Text(Store.appLabel(g.pkg) + " · " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(g.time)), style = MaterialTheme.typography.labelSmall, color = muted)
                    Text(g.text.take(90), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun NoteField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 200) onChange(it) }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") },
        minLines = 2, maxLines = 3, shape = MaterialTheme.shapes.medium, supportingText = { Text("${value.length}/200") })
}

@Composable
fun DebtGroupCard(name: String, list: List<Debt>, open: Boolean, onToggle: () -> Unit, onOpen: (Debt) -> Unit, onAdd: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bal = list.sumOf { it.balance }
    val tot = list.sumOf { it.total }
    val mon = list.filter { it.balance > 0.005 }.sumOf { it.monthly }
    val fin = list.mapNotNull { Store.payoff(it) }.maxByOrNull { it.second }
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CatAvatar(name)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text("${list.size} item${if (list.size > 1) "s" else ""} · ${mk(rm(mon))}/month", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand", tint = muted)
            }
            BudgetBar(tot - bal, tot, color = MaterialTheme.colorScheme.tertiary)
            Text("${mk(rm(bal))} left of ${mk(rm(tot))}", style = MaterialTheme.typography.bodyMedium)
            if (fin != null) Text("All done by ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(fin.second))}", style = MaterialTheme.typography.labelMedium, color = muted)
            if (open) {
                HorizontalDivider()
                list.forEach { d ->
                    val nd = Store.nextDue(d)
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onOpen(d) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name.removePrefix("$name · "), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${mk(rm(d.balance))} left" + (if (nd > 0 && d.balance > 0.005) " · next ${fmtDate(nd)}" else ""), style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = muted)
                    }
                }
                TextButton(onAdd, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add to $name") }
            }
        }
    }
}
