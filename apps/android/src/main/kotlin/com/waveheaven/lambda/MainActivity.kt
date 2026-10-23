package com.waveheaven.lambda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.waveheaven.lambda.shared.App
import com.waveheaven.lambda.spike.MeterService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
        // SPIKE #2: start the meter service from a visible activity (Android 14+ while-in-use rule).
        if (intent.getBooleanExtra(EXTRA_SPIKE_START, false)) {
            startForegroundService(
                Intent(this, MeterService::class.java)
                    .putExtra(MeterService.EXTRA_VOLUME_TEST, intent.getBooleanExtra(MeterService.EXTRA_VOLUME_TEST, false))
                    .putExtra(MeterService.EXTRA_VOLUME_PERIOD, intent.getIntExtra(MeterService.EXTRA_VOLUME_PERIOD, 5)),
            )
        }
    }

    private companion object {
        const val EXTRA_SPIKE_START = "spike_start"
    }
}
