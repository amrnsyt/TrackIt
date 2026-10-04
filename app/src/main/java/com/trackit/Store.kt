package com.trackit

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class Category(val name: String, val keywords: String)
data class Txn(val id: String, val amount: Double, val merchant: String, val category: String, val time: Long)

object Store {
    val cats = mutableStateListOf<Category>()
    val txns = mutableStateListOf<Txn>()
    private var ready = false
    private lateinit var ctx: Context
    private fun sp() = ctx.getSharedPreferences("trackit", Context.MODE_PRIVATE)

    @Synchronized fun init(c: Context) {
        if (ready) return
        ctx = c.applicationContext
        val s = sp()
        JSONArray(s.getString("cats", "[]")).let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { cats.add(Category(it.getString("n"), it.getString("k"))) }
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
        ready = true
    }

    private fun save() {
        val c = JSONArray().also { a -> cats.forEach { a.put(JSONObject().put("n", it.name).put("k", it.keywords)) } }
        val t = JSONArray().also { a -> txns.forEach { a.put(JSONObject().put("i", it.id).put("a", it.amount).put("m", it.merchant).put("c", it.category).put("t", it.time)) } }
        sp().edit().putString("cats", c.toString()).putString("txns", t.toString()).apply()
    }

    fun addCat(c: Category) { cats.add(c); save() }
    fun delCat(c: Category) { cats.remove(c); save() }
    fun delTxn(t: Txn) { txns.remove(t); save() }
    fun setCat(t: Txn, cat: String) { val i = txns.indexOf(t); if (i >= 0) { txns[i] = t.copy(category = cat); save() } }
    fun addTxn(t: Txn) { if (txns.none { it.id == t.id }) { txns.add(0, t); save() } }
    fun classify(text: String): String {
        val l = text.lowercase()
        return cats.firstOrNull { c -> c.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }.any { l.contains(it) } }?.name ?: "Uncategorized"
    }
}
