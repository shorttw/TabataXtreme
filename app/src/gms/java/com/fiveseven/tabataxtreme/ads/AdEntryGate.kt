package com.fiveseven.tabataxtreme.ads

import android.content.Context
import androidx.activity.ComponentActivity
import com.fiveseven.tabataxtreme.EntryGate
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Shows a Google consent form (first run only) and an app-open ad, skipping the first few launches. */
class AdEntryGate(
    private val adManager: AppOpenAdManager,
) : EntryGate {

    override suspend fun awaitReady(activity: ComponentActivity) {
        val launchCount = incrementAndGetLaunchCount(activity)
        val consentWaitMs = 1_000L
        val adWaitMs = 2_000L

        if (launchCount < 4) return

        val consentInfo: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        var consentDone = false
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    consentDone = true
                }
            },
            {
                consentDone = true
            }
        )

        val consentStart = System.currentTimeMillis()
        while (!consentDone && System.currentTimeMillis() - consentStart < consentWaitMs) {
            delay(50)
        }

        if (!consentInfo.canRequestAds()) return

        adManager.loadAd()
        val adStart = System.currentTimeMillis()
        while (!adManager.isAdAvailable() && System.currentTimeMillis() - adStart < adWaitMs) {
            delay(50)
        }

        suspendCancellableCoroutine<Unit> { cont ->
            val didShow = adManager.showIfAvailable(activity) {
                if (cont.isActive) cont.resume(Unit)
            }
            if (!didShow && cont.isActive) cont.resume(Unit)
        }
    }

    private fun incrementAndGetLaunchCount(context: Context): Int {
        val prefs = context.getSharedPreferences("tw_prefs", Context.MODE_PRIVATE)
        val next = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", next).apply()
        return next
    }
}
