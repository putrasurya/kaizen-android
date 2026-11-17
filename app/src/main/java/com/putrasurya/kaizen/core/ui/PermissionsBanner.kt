package com.putrasurya.kaizen.core.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.putrasurya.kaizen.core.system.ExactAlarms

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Shows what's missing for on-time alarms with sound: notifications and
 * "Alarms & reminders". Hidden when both are allowed. Re-checks when the user
 * comes back from Settings.
 */
@Composable
fun PermissionsBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(hasNotificationPermission(context)) }
    var exactAlarms by remember { mutableStateOf(ExactAlarms.canSchedule(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = hasNotificationPermission(context)
        exactAlarms = ExactAlarms.canSchedule(context)
    }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifications = granted }

    if (notifications && exactAlarms) return

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Allow these so timers ring on time", style = MaterialTheme.typography.titleSmall)
            if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TextButton(onClick = {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Allow notifications") }
            }
            if (!exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + context.packageName),
                        )
                    )
                }) { Text("Allow alarms & reminders") }
            }
        }
    }
}
