package com.ronitgandhi.motionfuel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ronitgandhi.motionfuel.MainActivity
import com.ronitgandhi.motionfuel.R

class MotionFuelWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        private const val Preferences = "motionfuel_widget"
        fun publish(context: Context, caloriesRemaining: Int, steps: Long, waterMl: Int) {
            context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit()
                .putInt("calories", caloriesRemaining)
                .putLong("steps", steps)
                .putInt("water", waterMl)
                .apply()
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MotionFuelWidgetProvider::class.java)
            manager.updateAppWidget(component, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val data = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.motionfuel_widget).apply {
                setTextViewText(R.id.widget_calories, "${data.getInt("calories", 0)} kcal left")
                setTextViewText(R.id.widget_steps, "${data.getLong("steps", 0)} steps")
                setTextViewText(R.id.widget_water, "${data.getInt("water", 0)} ml water")
                setOnClickPendingIntent(R.id.widget_root, openApp)
            }
        }
    }
}
