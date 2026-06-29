package com.fiveseven.tabataxtreme.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.FullScreenContentCallback
import java.util.concurrent.atomic.AtomicBoolean


class AppOpenAdManager(private val context: Context) {

    var adUnitId: String = "ca-app-pub-8839111735981435/4579086058" // Real Ad Unit Id
    // var adUnitId: String = "ca-app-pub-3940256099942544/9257395921" // Test Ad Unit id

    private var appOpenAd: AppOpenAd? = null
    private var loadTimeMs: Long = 0L

    private val isLoading = AtomicBoolean(false)
    private val isShowing = AtomicBoolean(false)

    fun loadAd() {
        // Don’t load if we already have a fresh ad or are currently loading
        if (isAdAvailable()) return
        if (isLoading.getAndSet(true)) return

        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            context,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTimeMs = SystemClock.elapsedRealtime()
                    isLoading.set(false)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    isLoading.set(false)
                }
            }
        )
    }

    /** Public so UI can wait briefly for readiness (e.g., up to 3s on cold start). */
    fun isAdAvailable(): Boolean {
        appOpenAd ?: return false
        val ageMs = SystemClock.elapsedRealtime() - loadTimeMs
        // Google sample commonly treats app open ads as valid for 4 hours.
        return ageMs < 4 * 60 * 60 * 1000
    }

    /**
     * @return true if an ad began showing (caller should wait for onComplete),
     *         false if no ad was shown and caller should proceed immediately.
     */
    fun showIfAvailable(activity: Activity, onComplete: () -> Unit): Boolean {
        // Prevent re-entrant show attempts
        if (isShowing.get()) return true

        if (!isAdAvailable()) {
            loadAd()
            onComplete()
            return false
        }

        val ad = appOpenAd ?: run {
            loadAd()
            onComplete()
            return false
        }

        isShowing.set(true)

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowing.set(false)
                loadAd()
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowing.set(false)
                loadAd()
                onComplete()
            }

            override fun onAdShowedFullScreenContent() {
                // Ad is now consumed; prevent reuse.
                appOpenAd = null
            }
        }

        try {
            ad.show(activity)
            return true
        } catch (t: Throwable) {
            appOpenAd = null
            isShowing.set(false)
            loadAd()
            onComplete()
            return false
        }
    }
}
