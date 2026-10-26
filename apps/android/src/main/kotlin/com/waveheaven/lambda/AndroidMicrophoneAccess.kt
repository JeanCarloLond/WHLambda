package com.waveheaven.lambda

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.waveheaven.lambda.core.domain.consent.MicrophoneAccess

/**
 * [MicrophoneAccess] for one activity. Must be created before the activity is started, because the permission
 * launcher has to be registered during `onCreate`.
 */
class AndroidMicrophoneAccess(
    private val activity: ComponentActivity,
) : MicrophoneAccess {
    private var pending: ((Boolean) -> Unit)? = null

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pending?.invoke(granted)
            pending = null
        }

    override fun isGranted(): Boolean = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun request(onResult: (granted: Boolean) -> Unit) {
        if (isGranted()) {
            onResult(true)
        } else {
            pending = onResult
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun openSystemSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }
}
