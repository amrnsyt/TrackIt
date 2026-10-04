package com.trackit

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        intent.getStringExtra("txn")?.let { Store.openId.value = it }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val scheme = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(
                colorScheme = scheme,
                shapes = Shapes(
                    small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(24.dp),
                    large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(40.dp)
                )
            ) { App() }
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
    "DAILY" -> "Daily"; "WEEKLY" -> "Weekly"; "YEARLY" -> "Yearly"; else -> "Monthly (day ${c.startDay})"
}

@Composable
fun barColor(v: Double, b: Double): Color = when {
    b <= 0 -> MaterialTheme.colorScheme.primary
    v > b -> MaterialTheme.colorScheme.error
    v >= 0.8 * b -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun CatBar(name: String, spent: Double, budget: Double, share: Float, sub: String = "") {
    val f = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else share
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(if (budget > 0) "${rm(spent)} / ${rm(budget)}" else rm(spent))
        }
        LinearProgressIndicator(
            progress = { f }, color = barColor(spent, budget),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(10.dp), strokeCap = StrokeCap.Round
        )
        val st = if (budget > 0) (if (spent > budget) "Over by ${rm(spent - budget)}" else "Left ${rm(budget - spent)}") else ""
        val line = listOf(sub, st).filter { it.isNotEmpty() }.joinToString(" • ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(listenerOn(ctx)) }
    var dlg by remember { mutableStateOf(false) }
    var addT by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var menu by remember { mutableStateOf(false) }
    val bk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val ok = runCatching { ctx.contentResolver.openOutputStream(it)!!.use { o -> o.write(Store.backup().toByteArray()) } }.isSuccess
            Toast.makeText(ctx, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }
    val rs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val ok = runCatching { Store.restore(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText()) }.isSuccess
            Toast.makeText(ctx, if (ok) "Data restored" else "Invalid backup file", Toast.LENGTH_SHORT).show()
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { enabled = listenerOn(ctx); Store.rollover() }
        }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Track It!", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Backup data") }, onClick = { menu = false; bk.launch("TrackIt-backup.json") })
                        DropdownMenuItem(text = { Text("Restore data") }, onClick = { menu = false; rs.launch(arrayOf("*/*")) })
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Receipt, null) }, label = { Text("Activity") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Category, null) }, label = { Text("Budgets") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.History, null) }, label = { Text("History") })
            }
        },
        floatingActionButton = {
            if (tab == 1) ExtendedFloatingActionButton(
                onClick = { editing = null; dlg = true },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New category") })
            else if (tab == 0) ExtendedFloatingActionButton(
                onClick = { addT = true },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add payment") })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            if (!enabled) {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Allow notification access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Needed to detect payment alerts.", Modifier.padding(vertical = 4.dp))
                        Button(onClick = { ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }) { Text("Open settings") }
                    }
                }
            }
            when (tab) {
                0 -> Activity()
                1 -> Categories { editing = it; dlg = true }
                else -> History()
            }
        }
    }
    if (dlg) CatDialog(editing) { dlg = false }
    if (addT) AddTxnDialog { addT = false }
    Store.openId.value?.let { id ->
        val t = Store.txns.find { it.id == id }
        if (t != null) { EditTxnDialog(t) { Store.openId.value = null } } else { Store.openId.value = null }
    }
}

@Composable
fun Activity() {
    var pick by remember { mutableStateOf<Txn?>(null) }
    val rows = Store.cats.map { it to Store.spent(it) }
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val unc = Store.txns.filter { it.category == "Uncategorized" && it.time >= cal.timeInMillis }.sumOf { it.amount }
    val total = rows.sumOf { it.second } + unc
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth().animateContentSize(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Spent this period", style = MaterialTheme.typography.labelLarge)
                    Text(rm(total), fontSize = 40.sp, fontWeight = FontWeight.Black)
                    rows.filter { it.second > 0 || it.first.budget > 0 }.sortedByDescending { it.second }.forEach { (c, v) ->
                        CatBar(c.name, v, c.budget, if (total > 0) (v / total).toFloat() else 0f, periodLabel(c))
                    }
                    if (unc > 0) CatBar("Uncategorized", unc, 0.0, if (total > 0) (unc / total).toFloat() else 0f, "This month")
                }
            }
        }
        if (Store.txns.isEmpty()) item {
            Text("No payments yet. Pay with QR / Paywave and the alert will appear here.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        }
        items(Store.txns.toList(), key = { it.id }) { t ->
            ListItem(
                modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { pick = t },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                headlineContent = { Text(t.merchant, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(t.category + " • " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(t.time))) },
                trailingContent = { Text(rm(t.amount), fontWeight = FontWeight.Bold) },
                leadingContent = { Icon(Icons.Filled.Payments, null) },
            )
        }
    }
    pick?.let { t -> EditTxnDialog(t) { pick = null } }
}

@Composable
fun Categories(onEdit: (Category) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(Store.cats.toList(), key = { it.name }) { c ->
            Card(
                Modifier.fillMaxWidth().clickable { onEdit(c) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.Edit, "Edit")
                    }
                    CatBar("Spent", Store.spent(c), c.budget, 0f, periodLabel(c) + " • from " + fmtDate(c.periodStart))
                    Text(c.keywords.ifBlank { "No keywords" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun History() {
    if (Store.snaps.isEmpty()) {
        Text("No past periods yet. When a budget period ends, its summary is saved here.", Modifier.padding(24.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(Store.snaps.toList(), key = { it.id }) { s ->
            Card(
                Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(s.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(fmtDate(s.start) + " – " + fmtDate(s.end - 1), style = MaterialTheme.typography.labelMedium)
                    CatBar("Spent", s.spent, s.budget, 0f)
                }
            }
        }
    }
}

@Composable
fun CatDialog(edit: Category?, close: () -> Unit) {
    var n by remember { mutableStateOf(edit?.name ?: "") }
    var k by remember { mutableStateOf(edit?.keywords ?: "") }
    var b by remember { mutableStateOf(edit?.budget?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "") }
    var p by remember { mutableStateOf(edit?.period ?: "MONTHLY") }
    var d by remember { mutableStateOf((edit?.startDay ?: 1).toString()) }
    val dup = Store.cats.any { it.name.equals(n.trim(), true) && it.name != edit?.name }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (edit == null) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true, isError = dup,
                    supportingText = { if (dup) Text("Name already used") })
                OutlinedTextField(k, { k = it }, label = { Text("Keywords (comma separated)") })
                OutlinedTextField(b, { b = it }, label = { Text("Budget RM (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Reset budget", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAILY" to "Day", "WEEKLY" to "Week", "MONTHLY" to "Month", "YEARLY" to "Year").forEach { (key, lbl) ->
                        FilterChip(selected = p == key, onClick = { p = key }, label = { Text(lbl) })
                    }
                }
                if (p == "MONTHLY") OutlinedTextField(d, { d = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Reset on day of month (1-28)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (edit != null) TextButton(onClick = { Store.delCat(edit); close() }) {
                    Text("Delete category", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = n.isNotBlank() && !dup, onClick = {
                val nc = Category(n.trim(), k.trim(), b.replace(",", ".").toDoubleOrNull() ?: 0.0, p,
                    d.toIntOrNull()?.coerceIn(1, 28) ?: 1, edit?.periodStart ?: 0L)
                if (edit == null) Store.addCat(nc) else Store.editCat(edit, nc)
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxnDialog(close: () -> Unit) {
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Uncategorized") }
    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    val v = amt.replace(",", ".").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Add payment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(amt, { amt = it }, label = { Text("Amount RM") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, label = { Text("Merchant / note") }, singleLine = true)
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Store.cats.map { it.name } + "Uncategorized").forEach { n ->
                        FilterChip(selected = cat == n, onClick = { cat = n }, label = { Text(n) })
                    }
                }
                OutlinedButton(onClick = { pickDate = true }) {
                    Icon(Icons.Filled.Event, null); Spacer(Modifier.width(8.dp)); Text(fmtDate(time))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = v != null && v > 0, onClick = {
                Store.addManual(Txn("manual:${System.nanoTime()}", v!!, note.trim().ifBlank { "Manual" }, cat, time)); close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = time)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                        time = Calendar.getInstance().apply { set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
                    }
                    pickDate = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun EditTxnDialog(t: Txn, close: () -> Unit) {
    var amt by remember(t.id) { mutableStateOf("%.2f".format(t.amount)) }
    var mer by remember(t.id) { mutableStateOf(t.merchant) }
    var cat by remember(t.id) { mutableStateOf(t.category) }
    val v = amt.replace(",", ".").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Edit payment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(amt, { amt = it }, label = { Text("Amount RM") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(mer, { mer = it }, label = { Text("Merchant") }, singleLine = true)
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Store.cats.map { it.name } + "Uncategorized").forEach { n ->
                        FilterChip(selected = cat == n, onClick = { cat = n }, label = { Text(n) })
                    }
                }
                TextButton(onClick = { Store.delTxn(t); close() }) { Text("Delete payment", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = v != null && v > 0, onClick = {
                Store.updateTxn(t, t.copy(amount = v!!, merchant = mer.trim().ifBlank { t.merchant }, category = cat)); close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}
