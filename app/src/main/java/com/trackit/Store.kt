package com.trackit

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Category(
    val name: String, val keywords: String, val budget: Double = 0.0,
    val period: String = "MONTHLY", val startDay: Int = 1, val periodStart: Long = 0L
)
data class Txn(val id: String, val amount: Double, val merchant: String, val category: String, val time: Long)
data class Snap(val id: String, val category: String, val start: Long, val end: Long, val budget: Double, val spent: Double)

object Store {
    val cats = mutableStateListOf<Category>()
    val txns = mutableStateListOf<Txn>()
    val snaps = mutableStateListOf<Snap>()
    private var ready = false
    private lateinit var ctx: Context
    private fun sp() = ctx.getSharedPreferences("trackit", Context.MODE_PRIVATE)

    val openId = mutableStateOf<String?>(null)

    @Synchronized fun init(c: Context) {
        if (ready) return
        ctx = c.applicationContext
        load()
        ready = true
        rollover()
    }

    private fun load() {
        cats.clear(); txns.clear(); snaps.clear()
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
                txns.add(Txn(it.getString("i"), it.getDouble("a"), it.getString("m"), it.getString("c"), it.getLong("t")))
            }
        }
        JSONArray(s.getString("snaps", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                snaps.add(Snap(it.getString("i"), it.getString("c"), it.getLong("s"), it.getLong("e"), it.getDouble("b"), it.getDouble("a")))
            }
        }
    }

    fun backup(): String {
        val s = sp()
        return JSONObject().put("app", "TrackIt").put("cats", JSONArray(s.getString("cats", "[]")))
            .put("txns", JSONArray(s.getString("txns", "[]"))).put("snaps", JSONArray(s.getString("snaps", "[]"))).toString()
    }

    fun restore(json: String) {
        val o = JSONObject(json)
        val c = o.getJSONArray("cats"); val t = o.getJSONArray("txns"); val h = o.optJSONArray("snaps") ?: JSONArray()
        sp().edit().putString("cats", c.toString()).putString("txns", t.toString()).putString("snaps", h.toString()).apply()
        load(); rollover()
    }

    private fun save() {
        val c = JSONArray().also { a -> cats.forEach { a.put(JSONObject().put("n", it.name).put("k", it.keywords).put("b", it.budget).put("p", it.period).put("d", it.startDay).put("s", it.periodStart)) } }
        val t = JSONArray().also { a -> txns.forEach { a.put(JSONObject().put("i", it.id).put("a", it.amount).put("m", it.merchant).put("c", it.category).put("t", it.time)) } }
        val h = JSONArray().also { a -> snaps.forEach { a.put(JSONObject().put("i", it.id).put("c", it.category).put("s", it.start).put("e", it.end).put("b", it.budget).put("a", it.spent)) } }
        sp().edit().putString("cats", c.toString()).putString("txns", t.toString()).putString("snaps", h.toString()).apply()
    }

    fun startOf(c: Category, now: Long = System.currentTimeMillis()): Long {
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
            val cur = startOf(c, now)
            if (c.periodStart == 0L) { cats[i] = c.copy(periodStart = cur); changed = true; continue }
            if (c.periodStart < cur) {
                var s = c.periodStart
                var n = 0
                while (s < cur && n < 36) {
                    val e = nextOf(c, s)
                    val sp = txns.filter { it.category == c.name && it.time >= s && it.time < e }.sumOf { it.amount }
                    if (c.budget > 0 || sp > 0) snaps.add(0, Snap("${c.name}:$s", c.name, s, e, c.budget, sp))
                    s = e; n++
                }
                cats[i] = c.copy(periodStart = cur); changed = true
            }
        }
        if (changed) save()
    }

    fun spent(c: Category): Double = txns.filter { it.category == c.name && it.time >= c.periodStart }.sumOf { it.amount }

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
    fun delTxn(t: Txn) { txns.remove(t); save() }
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
                save()
            }
            return null
        }
        txns.add(0, t); save()
        return t
    }
    fun updateTxn(old: Txn, n: Txn) {
        val i = txns.indexOf(old); if (i < 0) return
        txns[i] = n
        if (old.category == "Uncategorized" && n.category != "Uncategorized") learn(n.category, n.merchant)
        save()
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
    }
    private fun reclassify() {
        for (j in txns.indices) if (txns[j].category == "Uncategorized") {
            val c = classify(txns[j].merchant)
            if (c != "Uncategorized") txns[j] = txns[j].copy(category = c)
        }
    }
    fun classify(text: String): String {
        val l = text.lowercase()
        return cats.firstOrNull { c -> c.keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.any { l.contains(it) } }?.name ?: "Uncategorized"
    }
}
