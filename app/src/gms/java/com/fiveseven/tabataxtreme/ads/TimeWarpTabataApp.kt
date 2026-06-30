// SPDX-License-Identifier: MIT
package com.fiveseven.tabataxtreme.ads

import com.fiveseven.tabataxtreme.BaseApp
import com.fiveseven.tabataxtreme.EntryGate
import com.google.android.gms.ads.MobileAds

class TimeWarpTabataApp : BaseApp() {
    override lateinit var entryGate: EntryGate

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        val appOpenAdManager = AppOpenAdManager(this)
        appOpenAdManager.loadAd()
        entryGate = AdEntryGate(appOpenAdManager)
    }
}
