package com.grupo3.freno

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grupo3.freno.platform.FrenoNotificationListener
import com.grupo3.freno.data.FrenoEventStore
import com.grupo3.freno.ui.FrenoApp
import com.grupo3.freno.ui.PermissionSnapshot
import com.grupo3.freno.ui.theme.FrenoTheme

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile var isForeground: Boolean = false
            private set
    }

    private var permissions by mutableStateOf(PermissionSnapshot())
    private val requestAlertNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissions = permissions.copy(alertNotificationAccess = granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FrenoEventStore.initialize(applicationContext)
        setContent {
            FrenoTheme {
                FrenoApp(
                    permissions = permissions,
                    onRequestNotificationAccess = ::openNotificationAccess,
                    onRequestOverlayAccess = ::openOverlayAccess,
                    onRequestAlertNotificationAccess = ::openAlertNotificationAccess,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        requestAlertNotificationPermission()
        if (hasNotificationAccess()) {
            NotificationListenerService.requestRebind(ComponentName(this, FrenoNotificationListener::class.java))
        }
        permissions = PermissionSnapshot(
            notificationAccess = hasNotificationAccess(),
            overlayAccess = Settings.canDrawOverlays(this),
            alertNotificationAccess = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    private fun requestAlertNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) return
        val local = getSharedPreferences("freno_setup", MODE_PRIVATE)
        if (local.getBoolean("asked_alert_notifications", false)) return
        local.edit().putBoolean("asked_alert_notifications", true).apply()
        requestAlertNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onPause() {
        isForeground = false
        super.onPause()
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

    private fun openAlertNotificationAccess() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        })
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
