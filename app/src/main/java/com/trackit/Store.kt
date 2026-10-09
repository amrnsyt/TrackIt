package com.trackit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Category(
    val name: String, val keywords: String, val budget: Double = 0.0,
    val period: String = "MONTHLY", val startDay: Int = 1, val periodStart: Long = 0L
)
data class Txn(val id: String, val amount: Double, val merchant: String, val category: String, val time: Long, val income: Boolean = false, val counted: Boolean = true, val later: Boolean = false, val planId: String = "", val split: String = "", val billPay: Boolean = false)
data class Tpl(val id: String, val name: String, val amount: Double, val due: Int, val catName: String = "", val debtId: String = "")
data class Debt(val id: String, val name: String, val total: Double, val balance: Double, val monthly: Double, val due: Int, val firstDue: Long = 0L, val months: Int = 0)
data class DebtLog(val debtId: String, val itemId: String, val time: Long, val amount: Double)
data class Item(val id: String, val name: String, val amount: Double, val due: Int, val paid: Boolean = false, val txnId: String = "", val catName: String = "", val debtId: String = "")
data class Cycle(val start: Long, val salary: Double, val next: Long = 0L, val carry: Double = 0.0)
data class CSnap(val id: String, val start: Long, val end: Long, val salary: Double, val committed: Double, val spent: Double, val income: Double = 0.0, val carryIn: Double = 0.0, val carryOut: Double = 0.0)
data class Snap(val id: String, val category: String, val start: Long, val end: Long, val budget: Double, val spent: Double)

object Store {
    val cats = mutableStateListOf<Category>()
    val txns = mutableStateListOf<Txn>()
    val snaps = mutableStateListOf<Snap>()
    val tpls = mutableStateListOf<Tpl>()
    val items = mutableStateListOf<Item>()
    val csnaps = mutableStateListOf<CSnap>()
    val debts = mutableStateListOf<Debt>()
    val dlogs = mutableStateListOf<DebtLog>()
    val cycle = mutableStateOf<Cycle?>(null)
    private var ready = false
    private lateinit var ctx: Context
    private fun sp() = ctx.getSharedPreferences("trackit", Context.MODE_PRIVATE)

    private val mem = HashMap<String, HashMap<String, Int>>()
    val openId = mutableStateOf<String?>(null)
    val theme = mutableStateOf("SYSTEM")
    val hideBal = mutableStateOf(false)
    val remindOn = mutableStateOf(true)
    fun setRemind(v: Boolean) { remindOn.value = v; sp().edit().putBoolean("remind", v).apply() }
    var lastPing: String? = null
    fun setHide(v: Boolean) { hideBal.value = v; sp().edit().putBoolean("hide", v).apply() }
    private fun cancelPing() { ctx.getSystemService(NotificationManager::class.java).cancel(1001); lastPing = null }
    fun setTheme(v: String) { theme.value = v; sp().edit().putString("theme", v).apply() }

    @Synchronized fun init(c: Context) {
        if (ready) return
        ctx = c.applicationContext
        theme.value = sp().getString("theme", "SYSTEM") ?: "SYSTEM"
        hideBal.value = sp().getBoolean("hide", false)
        remindOn.value = sp().getBoolean("remind", true)
        load()
        ready = true
        rollover()
    }

    private fun load() {
        cats.clear(); txns.clear(); snaps.clear(); tpls.clear(); items.clear(); csnaps.clear(); debts.clear(); dlogs.clear()
        val s = sp()
        JSONArray(s.getString("cats", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                cats.add(Category(it.getString("n"), it.getString("k"), it.optDouble("b", 0.0),
                    it.optString("p", "MONTHLY"), it.optInt("d", 1), it.optLong("s", 0L)))
            }
        }
        if (cats.isEmpty()) cats.addAll(listOf(
            Category("Food", "mcdonald,kfc,starbucks,restaurant,cafe,grab food,foodpanda"),
            Category("Transport", "grab,petrol,petronas,shell,touch n go,tng,parking"),
            Category("Shopping", "shopee,lazada,mydin,aeon,giant,tesco,99 speedmart"),
            Category("Bills", "tnb,unifi,maxis,celcom,digi,water")))
        JSONArray(s.getString("txns", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                txns.add(Txn(it.getString("i"), it.getDouble("a"), it.getString("m"), it.getString("c"), it.getLong("t"), it.optBoolean("n", false), it.optBoolean("k", true), it.optBoolean("l", false), it.optString("pl", ""), it.optString("sp", ""), it.optBoolean("bp", false)))
            }
        }
        JSONArray(s.getString("snaps", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                snaps.add(Snap(it.getString("i"), it.getString("c"), it.getLong("s"), it.getLong("e"), it.getDouble("b"), it.getDouble("a")))
            }
        }
        JSONArray(s.getString("tpl", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { tpls.add(Tpl(it.getString("i"), it.getString("n"), it.getDouble("a"), it.getInt("d"), it.optString("g", ""), it.optString("b", ""))) }
        }
        JSONArray(s.getString("items", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { items.add(Item(it.getString("i"), it.getString("n"), it.getDouble("a"), it.getInt("d"), it.optBoolean("p", false), it.optString("t", ""), it.optString("g", ""), it.optString("b", ""))) }
        }
        JSONArray(s.getString("csnaps", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { csnaps.add(CSnap(it.getString("i"), it.getLong("s"), it.getLong("e"), it.getDouble("y"), it.getDouble("c"), it.getDouble("x"), it.optDouble("n", 0.0), it.optDouble("ci", 0.0), it.optDouble("co", 0.0))) }
        }
        JSONArray(s.getString("debts", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { debts.add(Debt(it.getString("i"), it.getString("n"), it.getDouble("t"), it.getDouble("b"), it.getDouble("m"), it.getInt("d"), it.optLong("f", 0L), it.optInt("k", 0))) }
        }
        JSONArray(s.getString("dlogs", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { dlogs.add(DebtLog(it.getString("d"), it.getString("i"), it.getLong("t"), it.getDouble("a"))) }
        }
        mem.clear()
        JSONObject(s.getString("mem", "{}") ?: "{}").let { o -> o.keys().forEach { k -> val c = o.getJSONObject(k); val mm = HashMap<String, Int>(); c.keys().forEach { cc -> mm[cc] = c.getInt(cc) }; mem[k] = mm } }
        if (mem.isEmpty()) txns.filter { !it.income }.forEach { learnMerchant(it.merchant, it.category, 1, false) }
        val pids = debts.filter { it.months > 0 }.map { it.id }
        if (pids.isNotEmpty()) {
            items.removeAll { it.id in pids }; tpls.removeAll { it.id in pids }
            for (i in dlogs.indices) if (dlogs[i].debtId in pids && dlogs[i].itemId in pids) dlogs[i] = dlogs[i].copy(itemId = "bill")
        }
        val cy = s.getString("cyc", "") ?: ""
        cycle.value = if (cy.isBlank()) null else JSONObject(cy).let { Cycle(it.getLong("s"), it.getDouble("y"), it.optLong("x", 0L), it.optDouble("c", 0.0)) }
    }

    fun backup(): String {
        val s = sp()
        return JSONObject().put("app", "TrackIt").put("cats", JSONArray(s.getString("cats", "[]")))
            .put("txns", JSONArray(s.getString("txns", "[]"))).put("snaps", JSONArray(s.getString("snaps", "[]")))
            .put("tpl", JSONArray(s.getString("tpl", "[]"))).put("items", JSONArray(s.getString("items", "[]")))
            .put("csnaps", JSONArray(s.getString("csnaps", "[]"))).put("cyc", s.getString("cyc", "")).put("debts", JSONArray(s.getString("debts", "[]"))).put("dlogs", JSONArray(s.getString("dlogs", "[]"))).put("mem", JSONObject(s.getString("mem", "{}"))).toString()
    }

    fun restore(json: String) {
        val o = JSONObject(json)
        val c = o.getJSONArray("cats"); val t = o.getJSONArray("txns"); val h = o.optJSONArray("snaps") ?: JSONArray()
        sp().edit().putString("cats", c.toString()).putString("txns", t.toString()).putString("snaps", h.toString())
            .putString("tpl", (o.optJSONArray("tpl") ?: JSONArray()).toString()).putString("items", (o.optJSONArray("items") ?: JSONArray()).toString())
            .putString("csnaps", (o.optJSONArray("csnaps") ?: JSONArray()).toString()).putString("cyc", o.optString("cyc", "")).putString("debts", (o.optJSONArray("debts") ?: JSONArray()).toString()).putString("dlogs", (o.optJSONArray("dlogs") ?: JSONArray()).toString()).putString("mem", (o.optJSONObject("mem") ?: JSONObject()).toString()).apply()
        load(); rollover()
    }

    private fun save() {
        val c = JSONArray().also { a -> cats.forEach { a.put(JSONObject().put("n", it.name).put("k", it.keywords).put("b", it.budget).put("p", it.period).put("d", it.startDay).put("s", it.periodStart)) } }
        val t = JSONArray().also { a -> txns.forEach { a.put(JSONObject().put("i", it.id).put("a", it.amount).put("m", it.merchant).put("c", it.category).put("t", it.time).put("n", it.income).put("k", it.counted).put("l", it.later).put("pl", it.planId).put("sp", it.split).put("bp", it.billPay)) } }
        val h = JSONArray().also { a -> snaps.forEach { a.put(JSONObject().put("i", it.id).put("c", it.category).put("s", it.start).put("e", it.end).put("b", it.budget).put("a", it.spent)) } }
        sp().edit().putString("cats", c.toString()).putString("txns", t.toString()).putString("snaps", h.toString()).apply()
        refreshWidget()
    }

    fun startOf(c: Category, now: Long = System.currentTimeMillis()): Long {
        if (c.period == "CYCLE") return cycle.value?.start ?: startOf(c.copy(period = "MONTHLY"), now)
        val cal = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (c.period) {
            "DAILY" -> {}
            "WEEKLY" -> cal.add(Calendar.DAY_OF_MONTH, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))
            "YEARLY" -> cal.set(Calendar.DAY_OF_YEAR, 1)
            else -> { cal.set(Calendar.DAY_OF_MONTH, c.startDay.coerceIn(1, 28)); if (cal.timeInMillis > now) cal.add(Calendar.MONTH, -1) }
        }
        return cal.timeInMillis
    }

    fun nextOf(c: Category, start: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        when (c.period) {
            "DAILY" -> cal.add(Calendar.DAY_OF_MONTH, 1)
            "WEEKLY" -> cal.add(Calendar.DAY_OF_MONTH, 7)
            "YEARLY" -> cal.add(Calendar.YEAR, 1)
            else -> cal.add(Calendar.MONTH, 1)
        }
        return cal.timeInMillis
    }

    @Synchronized fun rollover() {
        if (!ready) return
        val now = System.currentTimeMillis()
        var changed = false
        for (i in cats.indices) {
            val c = cats[i]
            if (c.period == "CYCLE") { if (c.periodStart == 0L) { cats[i] = c.copy(periodStart = startOf(c, now)); changed = true }; continue }
            val cur = startOf(c, now)
            if (c.periodStart == 0L) { cats[i] = c.copy(periodStart = cur); changed = true; continue }
            if (c.periodStart < cur) {
                var s = c.periodStart
                var n = 0
                while (s < cur && n < 36) {
                    val e = nextOf(c, s)
                    val sp = txns.filter { !it.income && it.time >= s && it.time < e }.sumOf { catAmt(it, c.name) }
                    if (c.budget > 0 || sp > 0) snaps.add(0, Snap("${c.name}:$s", c.name, s, e, c.budget, sp))
                    s = e; n++
                }
                cats[i] = c.copy(periodStart = cur); changed = true
            }
        }
        if (changed) save()
    }

    private fun refreshWidget() { try { BalanceWidget.refresh(ctx) } catch (_: Exception) { } }
    fun exportCsv(): String {
        fun q(x: String) = "\"" + x.replace("\"", "\"\"") + "\""
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        val sb = StringBuilder("Date,Merchant,Amount,Type,Category,Notes\n")
        txns.sortedBy { it.time }.forEach { t ->
            val notes = listOfNotNull(if (t.later) "SPayLater" else null, if (t.billPay) "SPayLater bill payment" else null,
                if (t.split.isNotBlank()) "Split: " + parts(t).joinToString("; ") { String.format(java.util.Locale.US, "%s %.2f", it.first, it.second) } else null).joinToString(" | ")
            sb.append(f.format(java.util.Date(t.time))).append(',').append(q(t.merchant)).append(',').append(String.format(java.util.Locale.US, "%.2f", t.amount)).append(',')
                .append(if (t.income) "Income" else "Expense").append(',').append(q(t.category)).append(',').append(q(notes)).append('\n')
        }
        return sb.toString()
    }
    fun payoff(d: Debt): Pair<Int, Long>? {
        if (d.monthly <= 0 || d.balance <= 0.005) return null
        val n = Math.ceil(d.balance / d.monthly - 0.0001).toInt().coerceAtLeast(1)
        val c = Calendar.getInstance()
        if (d.months > 0) { val nd = nextDue(d); if (nd > 0) c.timeInMillis = nd }
        else if (d.due > 0) { c.set(Calendar.DAY_OF_MONTH, minOf(d.due, 28)); if (c.timeInMillis < System.currentTimeMillis()) c.add(Calendar.MONTH, 1) }
        else c.add(Calendar.MONTH, 1)
        c.add(Calendar.MONTH, n - 1)
        return n to c.timeInMillis
    }
    private fun saveC() {
        val t = JSONArray().also { a -> tpls.forEach { a.put(JSONObject().put("i", it.id).put("n", it.name).put("a", it.amount).put("d", it.due).put("g", it.catName).put("b", it.debtId)) } }
        val m = JSONArray().also { a -> items.forEach { a.put(JSONObject().put("i", it.id).put("n", it.name).put("a", it.amount).put("d", it.due).put("p", it.paid).put("t", it.txnId).put("g", it.catName).put("b", it.debtId)) } }
        val h = JSONArray().also { a -> csnaps.forEach { a.put(JSONObject().put("i", it.id).put("s", it.start).put("e", it.end).put("y", it.salary).put("c", it.committed).put("x", it.spent).put("n", it.income).put("ci", it.carryIn).put("co", it.carryOut)) } }
        val c = cycle.value?.let { JSONObject().put("s", it.start).put("y", it.salary).put("x", it.next).put("c", it.carry).toString() } ?: ""
        val dj = JSONArray().also { a -> debts.forEach { a.put(JSONObject().put("i", it.id).put("n", it.name).put("t", it.total).put("b", it.balance).put("m", it.monthly).put("d", it.due).put("f", it.firstDue).put("k", it.months)) } }
        val lj = JSONArray().also { a -> dlogs.forEach { a.put(JSONObject().put("d", it.debtId).put("i", it.itemId).put("t", it.time).put("a", it.amount)) } }
        sp().edit().putString("debts", dj.toString()).putString("dlogs", lj.toString()).putString("tpl", t.toString()).putString("items", m.toString()).putString("csnaps", h.toString()).putString("cyc", c).apply()
        refreshWidget()
    }
    fun cycleSpent(): Double { val c = cycle.value ?: return 0.0; return txns.filter { !it.income && !it.later && it.time >= c.start }.sumOf { it.amount } }
    fun cycleBought(): Double { val c = cycle.value ?: return 0.0; return txns.filter { !it.income && !it.billPay && it.time >= c.start }.sumOf { it.amount } }
    fun unpaid(): Double = items.filter { !it.paid }.sumOf { it.amount } + billReserve()
    fun cycleIncome(): Double { val c = cycle.value ?: return 0.0; return txns.filter { it.income && it.counted && it.time >= c.start }.sumOf { it.amount } }
    fun paidManual(): Double = items.filter { it.paid && it.txnId.isEmpty() }.sumOf { it.amount }
    fun spentAll(): Double = cycleSpent() + paidManual() + billCash()
    fun cashLeft(): Double = (cycle.value?.salary ?: 0.0) + (cycle.value?.carry ?: 0.0) + cycleIncome() - spentAll()
    fun cycleLeft(): Double = cashLeft() - unpaid()
    fun startCycle(salary: Double, start: Long, next: Long, chosen: List<Tpl>, carry: Double = 0.0) {
        val old = cycle.value
        if (old != null) csnaps.add(0, CSnap("c:${old.start}", old.start, start, old.salary, unpaid(),
            txns.filter { !it.income && !it.later && it.time >= old.start && it.time < start }.sumOf { it.amount } + paidManual() +
                dlogs.filter { it.itemId == "bill" && it.time >= old.start && it.time < start }.sumOf { it.amount },
            txns.filter { it.income && it.counted && it.time >= old.start && it.time < start }.sumOf { it.amount }, old.carry, carry))
        for (i in cats.indices) {
            val c = cats[i]
            if (c.period != "CYCLE") continue
            if (c.periodStart in 1 until start) {
                val sp = txns.filter { !it.income && it.time >= c.periodStart && it.time < start }.sumOf { catAmt(it, c.name) }
                if (c.budget > 0 || sp > 0) snaps.add(0, Snap("${c.name}:${c.periodStart}", c.name, c.periodStart, start, c.budget, sp))
            }
            cats[i] = c.copy(periodStart = start)
        }
        val keep = items.filter { i -> !i.paid && tpls.none { it.id == i.id } && chosen.none { it.id == i.id } }
        cycle.value = Cycle(start, salary, next, carry)
        items.clear(); items.addAll(chosen.map { Item(it.id, it.name, it.amount, it.due, false, "", it.catName, it.debtId) }); items.addAll(keep)
        save(); saveC()
    }
    fun editCycle(salary: Double, next: Long, carry: Double = 0.0) { cycle.value = cycle.value?.copy(salary = salary, next = next, carry = carry); saveC() }
    fun togglePaid(i: Item) { val k = items.indexOf(i); if (k >= 0) { items[k] = i.copy(paid = !i.paid, txnId = ""); payDebt(i, !i.paid); saveC() } }
    fun saveDebt(old: Debt?, n: Debt) {
        if (old == null) debts.add(n) else { val k = debts.indexOf(old); if (k >= 0) debts[k] = n }
        if (n.balance <= 0.005) tpls.removeAll { it.debtId == n.id }
        else if (n.months > 0) {
            for (k in tpls.indices) if (tpls[k].id == n.id) tpls[k] = tpls[k].copy(amount = n.monthly)
            for (k in items.indices) if (items[k].id == n.id && !items[k].paid) items[k] = items[k].copy(amount = n.monthly)
        }
        saveC()
    }
    fun delDebt(d: Debt) {
        debts.remove(d); dlogs.removeAll { it.debtId == d.id && !it.itemId.startsWith("bill") }
        if (txns.removeAll { it.planId == d.id }) save()
        for (k in items.indices) if (items[k].debtId == d.id) items[k] = items[k].copy(debtId = "")
        for (k in tpls.indices) if (tpls[k].debtId == d.id) tpls[k] = tpls[k].copy(debtId = "")
        saveC()
    }
    fun dueEnd(t: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = t; if (get(Calendar.DAY_OF_MONTH) > 20) add(Calendar.MONTH, 1) }
        c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    fun nextDue(d: Debt): Long {
        if (d.firstDue <= 0 || d.monthly <= 0) return 0L
        val paid = ((d.total - d.balance) / d.monthly + 0.0001).toInt().coerceIn(0, maxOf(d.months - 1, 0))
        val c = Calendar.getInstance().apply { timeInMillis = d.firstDue }
        if (c.get(Calendar.DAY_OF_MONTH) != 1) { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, 1) }
        c.add(Calendar.MONTH, paid)
        return c.timeInMillis
    }
    private fun clearPlan(id: String) {
        debts.removeAll { it.id == id }; dlogs.removeAll { it.debtId == id && !it.itemId.startsWith("bill") }; tpls.removeAll { it.id == id }; items.removeAll { it.id == id && !it.paid }
        saveC()
    }
    fun setLater(t: Txn, on: Boolean, months: Int, fee: Double, paid: Int = 0) {
        val i = txns.indexOfFirst { it.id == t.id }; if (i < 0) return
        val cur = txns[i]
        if (cur.planId.isNotBlank()) clearPlan(cur.planId)
        if (!on) { txns[i] = cur.copy(later = false, planId = ""); save(); return }
        val total = cur.amount * (1 + fee / 100.0 * months)
        val monthly = total / months
        val bal = (total - paid.coerceIn(0, months) * monthly).coerceAtLeast(0.0)
        val d = Debt(("pl" + cur.id.hashCode()), "SPayLater · ${cur.merchant}".take(40), total, bal, monthly, 31, dueEnd(cur.time), months)
        debts.add(d)
        if (bal > 0.005) {
            tpls.add(Tpl(d.id, d.name, monthly, 31, cur.category, d.id))
            if (cycle.value != null) items.add(Item(d.id, d.name, monthly, 31, false, "", cur.category, d.id))
        }
        txns[i] = cur.copy(later = true, planId = d.id)
        save(); saveC()
    }
    data class Bill(val month: Long, val total: Double, val parts: List<Pair<Debt, Double>>)
    fun amountDue(d: Debt): Double {
        val paid = d.total - d.balance
        val k = minOf(maxOf(d.months - 1, 0), Math.floor(paid / d.monthly + 0.0001).toInt())
        val instal = if (k == d.months - 1) d.total - d.monthly * k else d.monthly
        return minOf(d.balance, instal - (paid - d.monthly * k)).coerceAtLeast(0.0)
    }
    fun bill(): Bill? {
        val ps = debts.filter { it.months > 0 && it.monthly > 0 && it.balance > 0.005 }
        if (ps.isEmpty()) return null
        val due = ps.minOf { nextDue(it) }
        val parts = ps.filter { nextDue(it) == due }.map { it to amountDue(it) }
        return Bill(due, parts.sumOf { it.second }, parts)
    }
    private fun reserveLimit(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    fun billReserved(b: Bill): Boolean = b.month <= reserveLimit()
    private fun billReserve(): Double { val b = bill() ?: return 0.0; return if (billReserved(b)) b.total else 0.0 }
    private fun billCash(): Double { val c = cycle.value ?: return 0.0; return dlogs.filter { it.itemId == "bill" && it.time >= c.start }.sumOf { it.amount } }
    fun undoPayment(l: DebtLog) {
        val i = dlogs.indexOf(l); if (i < 0) return
        dlogs.removeAt(i)
        if (l.itemId.startsWith("billtx:")) { val ti = txns.indexOfFirst { it.id == l.itemId.removePrefix("billtx:") }; if (ti >= 0) { txns[ti] = txns[ti].copy(billPay = false); save() } }
        val k = debts.indexOfFirst { it.id == l.debtId }
        if (k >= 0) debts[k] = debts[k].copy(balance = debts[k].balance + l.amount)
        saveC()
    }
    fun payBill(amount: Double, txnId: String? = null) {
        val b = bill() ?: return
        var left = amount
        for ((d, due) in b.parts) {
            if (left <= 0.005) break
            val pay = minOf(left, due)
            val k = debts.indexOfFirst { it.id == d.id }; if (k < 0) continue
            debts[k] = debts[k].copy(balance = (debts[k].balance - pay).coerceAtLeast(0.0))
            dlogs.add(0, DebtLog(d.id, if (txnId != null) "billtx:$txnId" else "bill", System.currentTimeMillis(), pay))
            left -= pay
        }
        if (txnId != null) { val ti = txns.indexOfFirst { it.id == txnId }; if (ti >= 0) { txns[ti] = txns[ti].copy(billPay = true); save() } }
        saveC()
    }
    private fun autoBill(t: Txn): Boolean {
        val b = bill() ?: return false
        val m = t.merchant.lowercase()
        if (Math.abs(t.amount - b.total) > 0.005 || !(m.contains("shopee") || m.contains("spay"))) return false
        payBill(b.total, t.id); return true
    }
    fun addPlan(name: String, price: Double, months: Int, fee: Double, time: Long, paid: Int, cat: String, count: Boolean) {
        val total = Math.round(price * (1 + fee / 100.0 * months) * 100) / 100.0
        val monthly = Math.floor(total / months * 100) / 100.0
        val bal = if (paid >= months) 0.0 else (total - paid * monthly).coerceAtLeast(0.0)
        val id = "pl" + System.nanoTime()
        debts.add(Debt(id, "SPayLater · $name".take(40), total, bal, monthly, 31, dueEnd(time), months))
        if (count) {
            val l = (txns + Txn("pl:$id", price, name, cat, time, false, true, true, id)).sortedByDescending { it.time }
            txns.clear(); txns.addAll(l); save()
            if (cat != "Uncategorized") alert(cat)
        }
        saveC()
    }
    private fun payDebt(i: Item, paid: Boolean) {
        if (i.debtId.isBlank()) return
        val k = debts.indexOfFirst { it.id == i.debtId }; if (k < 0) return
        val d = debts[k]
        if (paid) { val nb = (d.balance - i.amount).coerceAtLeast(0.0); if (nb <= 0.005) tpls.removeAll { it.debtId == d.id }; debts[k] = d.copy(balance = nb); dlogs.add(0, DebtLog(d.id, i.id, System.currentTimeMillis(), i.amount)) }
        else { val li = dlogs.indexOfFirst { it.itemId == i.id && it.debtId == d.id }; if (li >= 0) { debts[k] = d.copy(balance = d.balance + dlogs[li].amount); dlogs.removeAt(li) } }
    }
    fun saveItem(old: Item?, n: Item, toList: Boolean) {
        if (old == null) items.add(n) else { val k = items.indexOf(old); if (k >= 0) items[k] = n }
        val t = Tpl(n.id, n.name, n.amount, n.due, n.catName, n.debtId)
        val ti = tpls.indexOfFirst { it.id == n.id }
        if (ti >= 0) tpls[ti] = t else if (toList) tpls.add(t)
        saveC()
    }
    fun delItem(i: Item, fromList: Boolean) { items.remove(i); if (fromList) tpls.removeAll { it.id == i.id }; saveC() }

    fun parts(t: Txn): List<Pair<String, Double>> {
        if (t.split.isBlank()) return listOf(t.category to t.amount)
        val a = JSONArray(t.split)
        return (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getDouble(1) } }
    }
    fun catAmt(t: Txn, cat: String): Double = if (t.billPay) 0.0 else parts(t).filter { it.first == cat }.sumOf { it.second }
    fun setSplit(t: Txn, p: List<Pair<String, Double>>) {
        val i = txns.indexOfFirst { it.id == t.id }; if (i < 0) return
        if (p.isEmpty()) { txns[i] = txns[i].copy(split = ""); save(); return }
        val j = JSONArray().also { a -> p.forEach { a.put(JSONArray().put(it.first).put(it.second)) } }
        txns[i] = txns[i].copy(split = j.toString(), category = p.maxByOrNull { it.second }!!.first)
        save()
        p.map { it.first }.distinct().forEach { alert(it) }
    }
    private fun key(m: String) = m.trim().lowercase()
    private fun saveMem() {
        val o = JSONObject(); mem.forEach { (k, v) -> o.put(k, JSONObject(v as Map<*, *>)) }
        sp().edit().putString("mem", o.toString()).apply()
    }
    fun learnMerchant(m: String, c: String, w: Int, persist: Boolean = true) {
        if (m.isBlank() || c == "Uncategorized" || c == "Income") return
        val mm = mem.getOrPut(key(m)) { HashMap() }
        mm[c] = ((mm[c] ?: 0) + w).coerceAtLeast(0)
        if (mm[c] == 0) mm.remove(c)
        if (persist) saveMem()
    }
    fun history(m: String): List<Pair<String, Int>> = (mem[key(m)] ?: emptyMap<String, Int>()).toList().sortedByDescending { it.second }
    fun alts(m: String): List<String> = history(m).drop(1).map { it.first }.take(2)
    fun uncertain(m: String): Boolean { val h = history(m); if (h.size < 2) return false; val tot = h.sumOf { it.second }; return tot > 0 && h[1].second.toDouble() / tot >= 0.3 }
    fun classifyFor(m: String, text: String): String = history(m).firstOrNull { h -> cats.any { it.name == h.first } }?.first ?: classify(text)
    fun topCats(n: Int): List<String> = cats.map { it.name }.sortedByDescending { c -> txns.count { it.category == c } }.take(n)
    fun spent(c: Category): Double = txns.filter { !it.income && it.time >= c.periodStart }.sumOf { catAmt(it, c.name) }

    fun addCat(c: Category) { cats.add(c.copy(periodStart = startOf(c))); reclassify(); save() }
    fun editCat(old: Category, n: Category) {
        val i = cats.indexOf(old); if (i < 0) return
        val ps = if (old.period != n.period || old.startDay != n.startDay) startOf(n) else old.periodStart
        cats[i] = n.copy(periodStart = ps)
        if (old.name != n.name) for (j in txns.indices) if (txns[j].category == old.name) txns[j] = txns[j].copy(category = n.name)
        reclassify()
        save()
    }
    fun delCat(c: Category) { cats.remove(c); save() }
    fun delTxn(t: Txn) {
        txns.remove(t)
        val k = items.indexOfFirst { it.txnId == t.id }
        if (k >= 0) { payDebt(items[k], false); items[k] = items[k].copy(paid = false, txnId = ""); saveC() }
        if (t.id == lastPing) cancelPing()
        if (t.planId.isNotBlank()) clearPlan(t.planId)
        save()
    }
    fun setCat(t: Txn, cat: String) { val i = txns.indexOf(t); if (i >= 0) { txns[i] = t.copy(category = cat); save() } }
    private const val WALLET = "com.google.android.apps.walletnfcrel"
    fun addTxn(t: Txn): Txn? {
        rollover()
        if (txns.any { it.id == t.id }) return null
        val i = txns.indexOfFirst { Math.abs(it.amount - t.amount) < 0.005 && Math.abs(it.time - t.time) < 3 * 60 * 1000 }
        if (i >= 0) {
            val old = txns[i]
            if (t.id.startsWith(WALLET) && !old.id.startsWith(WALLET)) {
                txns[i] = t.copy(id = old.id, category = if (t.category == "Uncategorized") old.category else t.category)
                save(); alert(txns[i].category)
            }
            return null
        }
        txns.add(0, t); save()
        if (!t.income) { learnMerchant(t.merchant, t.category, 1); autoPay(t); if (!autoBill(t)) alert(t.category) }
        return t
    }
    fun updateTxn(old: Txn, n: Txn) {
        val i = txns.indexOf(old); if (i < 0) return
        txns[i] = n
        if (!n.income && n.category != old.category && n.category != "Uncategorized") {
            learnMerchant(n.merchant, n.category, 2)
            if (old.category != "Uncategorized") learnMerchant(old.merchant, old.category, -1)
        }
        save()
        if (old.id == lastPing) cancelPing()
        if (!n.income) alert(n.category)
    }
    private fun learn(cat: String, m: String) {
        val k = m.trim().lowercase()
        if (k.isEmpty() || k == "manual") return
        val i = cats.indexOfFirst { it.name == cat }; if (i < 0) return
        val c = cats[i]
        if (c.keywords.split(",").map { it.trim().lowercase() }.contains(k)) return
        cats[i] = c.copy(keywords = if (c.keywords.isBlank()) k else c.keywords.trim().trimEnd(',') + "," + k)
        reclassify()
    }
    fun addManual(t: Txn) {
        rollover()
        val l = (txns + t).sortedByDescending { it.time }
        txns.clear(); txns.addAll(l); save()
        if (!t.income) alert(t.category)
    }
    private fun alert(catName: String) {
        val c = cats.firstOrNull { it.name == catName } ?: return
        if (c.budget <= 0) return
        val sp = spent(c)
        val pct = sp / c.budget
        val lvl = if (pct >= 1.0) 100 else if (pct >= 0.8) 80 else 0
        val key = "al:${c.name}:${c.periodStart}"
        if (lvl <= sp().getInt(key, 0)) return
        sp().edit().putInt(key, lvl).apply()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("budget", "Budget alerts", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, c.name.hashCode(), Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val msg = if (lvl == 100) "%s is over budget: RM %.2f of RM %.2f".format(c.name, sp, c.budget) else "%s has used %d%% of its budget".format(c.name, (pct * 100).toInt())
        nm.notify(("b" + c.name).hashCode(), Notification.Builder(ctx, "budget").setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Budget alert").setContentText(msg).setContentIntent(pi).setAutoCancel(true).build())
    }
    private fun autoPay(t: Txn) {
        val m = t.merchant.lowercase()
        val k = items.indexOfFirst { !it.paid && Math.abs(it.amount - t.amount) < 0.005 && (m.contains(it.name.lowercase()) || it.name.lowercase().contains(m)) }
        if (k >= 0) {
            val it0 = items[k]
            items[k] = it0.copy(paid = true, txnId = t.id); payDebt(it0, true)
            if (it0.catName.isNotBlank() && t.category == "Uncategorized") { val ti = txns.indexOfFirst { it.id == t.id }; if (ti >= 0) txns[ti] = txns[ti].copy(category = it0.catName) }
            saveC(); save()
        }
    }
    private fun reclassify() {
        for (j in txns.indices) if (txns[j].category == "Uncategorized") {
            val c = classifyFor(txns[j].merchant, txns[j].merchant)
            if (c != "Uncategorized") txns[j] = txns[j].copy(category = c)
        }
    }
    fun classify(text: String): String {
        val l = text.lowercase()
        return cats.firstOrNull { c -> c.keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.any { l.contains(it) } }?.name ?: "Uncategorized"
    }
}
