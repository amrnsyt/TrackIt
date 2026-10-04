package com.trackit

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
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

    @Synchronized fun init(c: Context) {
        if (ready) return
        ctx = c.applicationContext
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
        ready = true
        rollover()
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

    fun addCat(c: Category) { cats.add(c.copy(periodStart = startOf(c))); save() }
    fun editCat(old: Category, n: Category) {
        val i = cats.indexOf(old); if (i < 0) return
        val ps = if (old.period != n.period || old.startDay != n.startDay) startOf(n) else old.periodStart
        cats[i] = n.copy(periodStart = ps)
        if (old.name != n.name) for (j in txns.indices) if (txns[j].category == old.name) txns[j] = txns[j].copy(category = n.name)
        save()
    }
    fun delCat(c: Category) { cats.remove(c); save() }
    fun delTxn(t: Txn) { txns.remove(t); save() }
    fun setCat(t: Txn, cat: String) { val i = txns.indexOf(t); if (i >= 0) { txns[i] = t.copy(category = cat); save() } }
    fun addTxn(t: Txn) { rollover(); if (txns.none { it.id == t.id }) { txns.add(0, t); save() } }
    fun classify(text: String): String {
        val l = text.lowercase()
        return cats.firstOrNull { c -> c.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }.any { l.contains(it) } }?.name ?: "Uncategorized"
    }
}
