package com.trackit

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
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
}

private fun listenerOn(c: android.content.Context): Boolean =
    Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
        ?.contains(ComponentName(c, PayListener::class.java).flattenToString()) == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(listenerOn(ctx)) }
    var addCat by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) enabled = listenerOn(ctx) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text("Track It!", fontWeight = FontWeight.Black) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Receipt, null) }, label = { Text("Activity") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Category, null) }, label = { Text("Categories") })
            }
        },
        floatingActionButton = {
            if (tab == 1) ExtendedFloatingActionButton(onClick = { addCat = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New category") })
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
            if (tab == 0) Activity() else Categories()
        }
    }
    if (addCat) CatDialog { addCat = false }
}

@Composable
fun Activity() {
    var pick by remember { mutableStateOf<Txn?>(null) }
    val total = Store.txns.sumOf { it.amount }
    val by = Store.txns.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth().animateContentSize(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Total spent", style = MaterialTheme.typography.labelLarge)
                    Text("RM %,.2f".format(total), fontSize = 44.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    by.forEach { (n, v) ->
                        val f = if (total > 0) (v / total).toFloat() else 0f
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(n, fontWeight = FontWeight.Medium); Text("RM %,.2f".format(v))
                        }
                        LinearProgressIndicator(progress = { f }, Modifier.fillMaxWidth().height(10.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
        }
        if (Store.txns.isEmpty()) item {
            Text("No payments yet. Pay with QR / Paywave and the alert will appear here.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        }
        items(Store.txns, key = { it.id }) { t ->
            ListItem(
                modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { pick = t },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                headlineContent = { Text(t.merchant, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(t.category + " • " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(t.time))) },
                trailingContent = { Text("RM %.2f".format(t.amount), fontWeight = FontWeight.Bold) },
                leadingContent = { Icon(Icons.Filled.Payments, null) },
            )
        }
    }
    pick?.let { t ->
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("Category") },
            text = {
                Column {
                    (Store.cats.map { it.name } + "Uncategorized").forEach { n ->
                        TextButton(onClick = { Store.setCat(t, n); pick = null }) { Text(n) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { Store.delTxn(t); pick = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Close") } }
        )
    }
}

@Composable
fun Categories() {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(Store.cats.toList(), key = { it.name }) { c ->
            ListItem(
                modifier = Modifier.clip(MaterialTheme.shapes.large),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                headlineContent = { Text(c.name, fontWeight = FontWeight.Bold) },
                supportingContent = { Text(c.keywords.ifBlank { "No keywords" }) },
                trailingContent = { IconButton(onClick = { Store.delCat(c) }) { Icon(Icons.Filled.Delete, "Delete") } }
            )
        }
    }
}

@Composable
fun CatDialog(close: () -> Unit) {
    var n by remember { mutableStateOf("") }
    var k by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(k, { k = it }, label = { Text("Keywords (comma separated)") })
            }
        },
        confirmButton = { TextButton(enabled = n.isNotBlank(), onClick = { Store.addCat(Category(n.trim(), k.trim())); close() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}
