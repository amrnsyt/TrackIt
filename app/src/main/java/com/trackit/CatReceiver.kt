package com.trackit

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CatReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.init(c)
        val id = i.getStringExtra("txn") ?: return
        val cat = i.getStringExtra("cat") ?: return
        val t = Store.txns.find { it.id == id } ?: return
        Store.updateTxn(t, t.copy(category = cat))
        c.getSystemService(NotificationManager::class.java).notify(1001,
            Notification.Builder(c, "rec").setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("RM %.2f • %s".format(t.amount, t.merchant)).setContentText("Moved to $cat").setAutoCancel(true).build())
    }
}
