package com.trackit

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PayListener : NotificationListenerService() {
    private val amt = Regex("(?:RM|MYR)\\s?([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val hint = listOf("paid", "payment", "purchase", "spent", "debit", "transaction", "txn", "charged", "paywave", "duitnow", "qr", "transfer", "pembayaran")

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
        Store.addTxn(Txn("${sbn.packageName}:${sbn.postTime}:$v", v, title.ifBlank { sbn.packageName }, Store.classify(all), sbn.postTime))
    }
}
