package com.fiveseven.tabataxtreme

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fiveseven.tabataxtreme.ui.AdSplashScreen
import com.fiveseven.tabataxtreme.ui.AppRoot
import com.fiveseven.tabataxtreme.ui.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        val app = application as BaseApp

        setContent {
            AppTheme {
                val factory = remember { TabataViewModel.factory(applicationContext) }
                val vm: TabataViewModel = viewModel(factory = factory)

                var gateOpen by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    app.entryGate.awaitReady(this@MainActivity)
                    gateOpen = true
                }

                if (!gateOpen) {
                    AdSplashScreen()
                } else {
                    AppRoot(vm = vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }
}