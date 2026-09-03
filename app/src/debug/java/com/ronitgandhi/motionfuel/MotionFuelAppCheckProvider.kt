package com.ronitgandhi.motionfuel

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object MotionFuelAppCheckProvider {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
