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
        ids.forEach { manager.updateAppWidget(it, createRemoteViews(context)) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // A launcher can retain a RemoteViews parcel containing resource IDs from the
        // previous APK. Publishing a fresh view immediately after an app update avoids
        // the launcher's generic "Error loading widget" placeholder.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) updateAll(context)
    }

    companion object {
        private const val Preferences = "motionfuel_widget"
        fun publish(context: Context, caloriesRemaining: Int, steps: Long, waterMl: Int) {
            context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit()
                .putInt("calories", caloriesRemaining.coerceAtLeast(0))
                .putLong("steps", steps.coerceAtLeast(0))
                .putInt("water", waterMl.coerceAtLeast(0))
                .apply()
            updateAll(context)
        }

        private fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MotionFuelWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, createRemoteViews(context)) }
        }

        internal fun createRemoteViews(context: Context): RemoteViews {
            val data = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.motionfuel_widget).apply {
                setTextViewText(R.id.widget_calories, context.getString(R.string.widget_calories, data.getInt("calories", 0)))
                setTextViewText(R.id.widget_steps, context.getString(R.string.widget_steps, data.getLong("steps", 0)))
                setTextViewText(R.id.widget_water, context.getString(R.string.widget_water, data.getInt("water", 0)))
                setOnClickPendingIntent(R.id.widget_root, openApp)
            }
        }
    }
}
