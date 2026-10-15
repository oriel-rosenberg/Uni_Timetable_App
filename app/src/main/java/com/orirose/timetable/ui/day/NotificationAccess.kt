package com.orirose.timetable.ui.day

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.orirose.timetable.R

/**
 * Returns a function to call when a reminder is set: it makes sure the app may show notifications,
 * asking for the permission the first time (Android 13+), and says so if they are blocked.
 */
@Composable
fun rememberNotificationAccess(): () -> Unit {
    val context = LocalContext.current
    val warn = { Toast.makeText(context, R.string.notifications_blocked, Toast.LENGTH_LONG).show() }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) warn()
    }
    return {
        val mustAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            mustAsk -> request.launch(Manifest.permission.POST_NOTIFICATIONS)
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> warn()
        }
    }
}
