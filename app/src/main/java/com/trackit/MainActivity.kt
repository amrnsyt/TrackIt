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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
        setContent { TrackItTheme { App() } }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val pager = rememberPagerState { 3 }
    var enabled by remember { mutableStateOf(listenerOn(ctx)) }
    var menu by remember { mutableStateOf(false) }
    var addT by remember { mutableStateOf(false) }
    var catSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
        modifier = Modifier.nestedScroll(sb.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Track It!", fontWeight = FontWeight.ExtraBold) }, scrollBehavior = sb,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Backup data") }, leadingIcon = { Icon(Icons.Filled.Upload, null) }, onClick = { menu = false; bk.launch("TrackIt-backup.json") })
                        DropdownMenuItem(text = { Text("Restore data") }, leadingIcon = { Icon(Icons.Filled.Download, null) }, onClick = { menu = false; rs.launch(arrayOf("*/*")) })
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf(Icons.Filled.Receipt to "Activity", Icons.Filled.PieChart to "Budgets", Icons.Filled.History to "History").forEachIndexed { i, (ic, l) ->
                    NavigationBarItem(pager.currentPage == i, { go(i) }, { Icon(ic, null) }, label = { Text(l) })
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(pager.currentPage < 2, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { if (pager.currentPage == 0) addT = true else { editing = null; catSheet = true } },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text(if (pager.currentPage == 0) "Add payment" else "New category") },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            AnimatedVisibility(!enabled) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.NotificationsActive, null)
                        Column(Modifier.weight(1f)) {
                            Text("Payments aren't being tracked", fontWeight = FontWeight.Bold)
                            Text("Allow notification access to detect payment alerts.", style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton({ ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }) { Text("Enable") }
                    }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> Activity(del, { go(1) })
                    1 -> Budgets { editing = it; catSheet = true }
                    else -> History()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Activity(onDelete: (Txn) -> Unit, goBudgets: () -> Unit) {
    var pick by remember { mutableStateOf<Txn?>(null) }
    val rows = Store.cats.map { it to Store.spent(it) }
    val monthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val unc = Store.txns.filter { it.category == "Uncategorized" && it.time >= monthStart }.sumOf { it.amount }
    val total = rows.sumOf { it.second } + unc
    val budgeted = rows.filter { it.first.budget > 0 }
    val bTotal = budgeted.sumOf { it.first.budget }; val bSpent = budgeted.sumOf { it.second }
    val shown by animateFloatAsState(total.toFloat(), tween(600), label = "total")
    val needs = Store.txns.count { it.category == "Uncategorized" }
    val groups = Store.txns.groupBy { dayLabel(it.time) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Column(Modifier.padding(24.dp).animateContentSize()) {
                    Text("Spent this period", style = MaterialTheme.typography.labelLarge)
                    Text(rm(shown.toDouble()), style = MaterialTheme.typography.displayMedium)
                    if (bTotal > 0) {
                        Spacer(Modifier.height(12.dp))
                        BudgetBar(bSpent, bTotal)
                        Text("${statusText(bSpent, bTotal)} of ${rm(bTotal)} budget", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("${Store.txns.size} payments", MaterialTheme.colorScheme.surface.copy(alpha = .5f), MaterialTheme.colorScheme.onPrimaryContainer)
                        if (needs > 0) Pill("$needs need a category", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        if (rows.any { it.second > 0 }) {
            item { SectionLabel("Categories") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rows.filter { it.second > 0 || it.first.budget > 0 }.sortedByDescending { it.second }, key = { it.first.name }) { (c, v) ->
                        Card(Modifier.width(158.dp), shape = MaterialTheme.shapes.large, onClick = goBudgets,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CatAvatar(c.name, 36.dp)
                                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(rm(v), style = MaterialTheme.typography.titleMedium)
                                if (c.budget > 0) BudgetBar(v, c.budget) else Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
        if (Store.txns.isEmpty()) item {
            EmptyState(Icons.Filled.Contactless, "No payments yet", "Pay with QR or Paywave and it appears here automatically. You can also add one manually.")
        }
        groups.forEach { (day, list) ->
            item(key = "h$day") { SectionLabel(day) }
            items(list, key = { it.id }) { t ->
                val st = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(t); true } else false })
                SwipeToDismissBox(st, Modifier.animateItem().clip(MaterialTheme.shapes.large), enableDismissFromStartToEnd = false, backgroundContent = {
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
fun Budgets(onEdit: (Category) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
        if (Store.cats.isEmpty()) item { EmptyState(Icons.Filled.PieChart, "No categories", "Create a category to group payments and set a budget.") }
        items(Store.cats.toList(), key = { it.name }) { c ->
            val v = Store.spent(c)
            Card(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large, onClick = { onEdit(c) },
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
                                color = if (v > c.budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    } else Text("${rm(v)} spent · no budget set", style = MaterialTheme.typography.bodyMedium)
                    if (c.keywords.isNotBlank()) Text(c.keywords, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
