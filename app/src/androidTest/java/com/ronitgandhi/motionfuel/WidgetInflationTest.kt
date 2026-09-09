package com.ronitgandhi.motionfuel

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronitgandhi.motionfuel.widget.MotionFuelWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetInflationTest {
    @Test
    fun launcherCanInflateAndPopulateWidgetRemoteViews() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MotionFuelWidgetProvider.publish(context, 1875, 6421, 1500)

        val view = MotionFuelWidgetProvider.createRemoteViews(context)
            .apply(context, FrameLayout(context))

        assertNotNull(view.findViewById<TextView>(R.id.widget_calories))
        assertEquals("1875 kcal left", view.findViewById<TextView>(R.id.widget_calories).text.toString())
        assertEquals("6421 steps", view.findViewById<TextView>(R.id.widget_steps).text.toString())
        assertEquals("1500 ml water", view.findViewById<TextView>(R.id.widget_water).text.toString())
    }
}
