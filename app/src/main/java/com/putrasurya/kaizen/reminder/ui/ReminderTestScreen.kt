package com.putrasurya.kaizen.reminder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.putrasurya.kaizen.reminder.ReminderReceiver
import com.putrasurya.kaizen.reminder.ReminderScheduler
import com.putrasurya.kaizen.reminder.SpeakService

/** Phase 0 prototype screen, kept under More as a quick check of alarm, notification and voice. */
@Composable
fun ReminderTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by remember { mutableStateOf(ReminderReceiver.DEFAULT_MESSAGE) }
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    var exactAlarmsAllowed by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }

    // Re-check permissions when the user comes back from the Settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = hasNotificationPermission(context)
                exactAlarmsAllowed = ReminderScheduler.canScheduleExact(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsAllowed = granted }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kaizen reminder test", style = MaterialTheme.typography.headlineSmall)

        Text("Notifications: " + if (notificationsAllowed) "allowed" else "not allowed")
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Allow notifications") }
        }

        Text("Exact alarms: " + if (exactAlarmsAllowed) "allowed" else "not allowed")
        if (!exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + context.packageName)
                    )
                )
            }) { Text("Allow alarms & reminders") }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("What the reminder says") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                ReminderScheduler.scheduleIn(context, 60_000, message)
                Toast.makeText(
                    context,
                    "Scheduled. Close the app and wait 1 minute.",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Remind me in 1 minute") }

        OutlinedButton(
            onClick = { SpeakService.start(context, message) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Speak now (test)") }

        Text(
            "Tip: on silent or vibrate, Kaizen only shows the notification and stays quiet.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
