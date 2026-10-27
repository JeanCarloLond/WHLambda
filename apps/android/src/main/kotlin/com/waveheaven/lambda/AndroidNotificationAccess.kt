package com.waveheaven.lambda

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.waveheaven.lambda.core.domain.consent.NotificationAccess

/**
 * [NotificationAccess] for one activity. Below Android 13 notifications need no runtime permission. Must be created
 * before the activity is started, because the permission launcher has to be registered during `onCreate`.
 */
class AndroidNotificationAccess(
    private val activity: ComponentActivity,
) : NotificationAccess {
    private var pending: ((Boolean) -> Unit)? = null

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pending?.invoke(granted)
            pending = null
        }

    override fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun request(onResult: (granted: Boolean) -> Unit) {
        if (isGranted()) {
            onResult(true)
        } else {
            pending = onResult
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
