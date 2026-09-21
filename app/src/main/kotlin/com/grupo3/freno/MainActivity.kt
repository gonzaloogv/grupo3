package com.grupo3.freno

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grupo3.freno.platform.FrenoNotificationListener
import com.grupo3.freno.ui.FrenoApp
import com.grupo3.freno.ui.PermissionSnapshot
import com.grupo3.freno.ui.theme.FrenoTheme

class MainActivity : ComponentActivity() {
    private var permissions by mutableStateOf(PermissionSnapshot())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FrenoTheme {
                FrenoApp(
                    permissions = permissions,
                    onRequestNotificationAccess = ::openNotificationAccess,
                    onRequestOverlayAccess = ::openOverlayAccess,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissions = PermissionSnapshot(
            notificationAccess = hasNotificationAccess(),
            overlayAccess = Settings.canDrawOverlays(this),
        )
    }

    private fun openNotificationAccess() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openOverlayAccess() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun hasNotificationAccess(): Boolean {
        val expected = ComponentName(this, FrenoNotificationListener::class.java)
        val enabledComponents = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabledComponents
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .contains(expected)
    }
}
