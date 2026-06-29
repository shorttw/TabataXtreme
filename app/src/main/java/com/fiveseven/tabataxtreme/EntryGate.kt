package com.fiveseven.tabataxtreme

import androidx.activity.ComponentActivity

/** Gates app entry behind whatever a flavor needs (e.g. ads + consent), or nothing at all. */
fun interface EntryGate {
    suspend fun awaitReady(activity: ComponentActivity)
}
