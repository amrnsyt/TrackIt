package com.trackit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class BalanceWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        Store.init(c)
        ids.forEach { m.updateAppWidget(it, build(c)) }
    }

    companion object {
        fun build(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_balance)
            val cyc = Store.cycle.value
            if (cyc == null) {
                v.setTextViewText(R.id.w_amount, "Set up salary")
                v.setTextViewText(R.id.w_sub, "Open Track It!")
            } else if (Store.hideBal.value) {
                v.setTextViewText(R.id.w_amount, "RM ••••")
                v.setTextViewText(R.id.w_sub, "Balance hidden")
            } else {
                val safe = Store.cycleLeft()
                val now = System.currentTimeMillis()
                val dl = cyc.takeIf { it.next > now }?.let { Math.ceil((it.next - now) / 86400000.0).toInt().coerceAtLeast(1) }
                v.setTextViewText(R.id.w_amount, rm(safe))
                v.setTextViewText(R.id.w_sub, "Balance ${rm(Store.cashLeft())}" + (if (dl != null && safe > 0) " · ${rm(safe / dl)}/day" else ""))
            }
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 4004, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return v
        }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, BalanceWidget::class.java)).forEach { m.updateAppWidget(it, build(c)) }
        }
    }
}
