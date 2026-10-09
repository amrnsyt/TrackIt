package com.trackit

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Reminders {
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 3003, Intent(c, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val t = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1) }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    fun run(c: Context) {
        Store.init(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("remind", "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 3004, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun post(id: Int, title: String, text: String) {
            nm.notify(id, Notification.Builder(c, "remind").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
        }
        if (Store.remindOn.value) {
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_MONTH)
            val lines = ArrayList<String>()
            Store.items.filter { !it.paid && it.due > 0 && it.due == day }.forEach { lines.add("${it.name} · ${rm(it.amount)}") }
            Store.debts.filter { it.months == 0 && it.due > 0 && it.due == day && it.balance > 0.005 }.forEach { lines.add("${it.name} · ${rm(it.monthly)}") }
            Store.bill()?.let { b -> if (b.month - todayStart() in 0..(2 * 86400000L)) lines.add("SPayLater bill · ${rm(b.total)}") }
            if (lines.isNotEmpty()) post(2002, "Due soon", lines.joinToString("\n"))
        }
        val cyc = Store.cycle.value
        if (Store.summaryOn.value && cyc != null) {
            if (Store.hideBal.value) post(2003, "Today's budget", "Open Track It! to see your safe-to-spend amount.")
            else {
                val safe = Store.cycleLeft()
                val dl = cyc.takeIf { it.next > System.currentTimeMillis() }?.let { Math.ceil((it.next - System.currentTimeMillis()) / 86400000.0).toInt().coerceAtLeast(1) }
                post(2003, "Today's budget", "Safe to spend ${rm(safe)}" + (if (dl != null && safe > 0) " · about ${rm(safe / dl)} per day for $dl days" else ""))
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Reminders.run(c) }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Reminders.schedule(c) }
}
