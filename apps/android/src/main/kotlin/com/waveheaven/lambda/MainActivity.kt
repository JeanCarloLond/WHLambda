package com.waveheaven.lambda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.waveheaven.lambda.shared.App

class MainActivity : ComponentActivity() {
    private val microphoneAccess = AndroidMicrophoneAccess(this)
    private val notificationAccess = AndroidNotificationAccess(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(appVersion = BuildConfig.VERSION_NAME, microphoneAccess = microphoneAccess, notificationAccess = notificationAccess)
        }
    }
}
