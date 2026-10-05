package com.trackit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PayListener : NotificationListenerService() {
    private val amt = Regex("(?:RM|MYR)\\s?([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val who = Regex("\\b(?:to|at)\\s+([^.\\n]+?)(?=\\.\\s|\\.$|\\s+REF|\\s+on\\s|\\s+with\\s|$)", RegexOption.IGNORE_CASE)
    private val incoming = Regex("\\b(received|credited|diterima|refund|cash ?in)\\b", RegexOption.IGNORE_CASE)
    private val hint = listOf("paid", "payment", "purchase", "spent", "debit", "transaction", "txn", "charged", "paywave", "duitnow", "qr", "transfer", "pembayaran", "received", "credited", "diterima", "refund")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        Store.init(applicationContext)
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val all = "$title $body"
        val m = amt.find(all) ?: return
        if (hint.none { all.lowercase().contains(it) }) return
        val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return
        val wallet = sbn.packageName == "com.google.android.apps.walletnfcrel"
        val merchant = (if (wallet) title else who.find(body)?.groupValues?.get(1)?.trim())
            .let { if (it.isNullOrBlank()) title.ifBlank { sbn.packageName } else it }.take(40)
        val inc = incoming.containsMatchIn(all)
        val t = Store.addTxn(Txn("${sbn.packageName}:${sbn.postTime}:$v", v, merchant, if (inc) "Income" else Store.classify(all), sbn.postTime, inc)) ?: return
        ping(t)
    }

    private fun ping(t: Txn) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rec", "Recorded payments", NotificationManager.IMPORTANCE_DEFAULT))
        val i = Intent(this, MainActivity::class.java).putExtra("txn", t.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(this, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "rec").setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("RM %.2f • %s".format(t.amount, t.merchant))
            .setContentText("${t.category} — tap to change")
            .setContentIntent(pi).setAutoCancel(true).build()
        Store.lastPing = t.id
        nm.notify(1001, n)
    }
}
