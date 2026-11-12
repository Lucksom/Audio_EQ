package com.audioeq

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audioeq.audio.MusicAppTracker
import com.audioeq.shizuku.ShizukuManager
import com.audioeq.ui.AudioEqScreen
import com.audioeq.ui.EqViewModel
import com.audioeq.ui.theme.AudioEqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchedSessionId = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1) ?: -1
        setContent {
            val eqViewModel: EqViewModel = viewModel()
            val state by eqViewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner, eqViewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        eqViewModel.onAppResumed()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val notifPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ ->
                eqViewModel.refreshNotificationAndSessions()
            }

            AudioEqTheme(
                themeMode = state.themeMode,
                seedHex = state.seedHex
            ) {
                LaunchedEffect(launchedSessionId) {
                    if (launchedSessionId > 0) {
                        eqViewModel.attachExternalAudioSession(launchedSessionId)
                    }
                }
                LaunchedEffect(state.masterEnabled) {
                    if (state.masterEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            eqViewModel.refreshNotificationAndSessions()
                        }
                    }
                }
                AudioEqScreen(
                    uiState = state,
                    viewModel = eqViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MusicAppTracker.isNotificationListenerEnabled(this)
        ShizukuManager.refresh()
    }
}
