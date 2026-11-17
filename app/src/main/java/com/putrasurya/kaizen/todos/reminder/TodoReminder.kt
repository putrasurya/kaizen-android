package com.putrasurya.kaizen.todos.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.putrasurya.kaizen.KaizenApp
import com.putrasurya.kaizen.core.system.ExactAlarms
import com.putrasurya.kaizen.reminder.SpeakService
import com.putrasurya.kaizen.todos.domain.TodoMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The evening reminder's settings, kept in SharedPreferences (off by default, 20:00). */
data class ReminderSettings(val enabled: Boolean, val hour: Int, val minute: Int) {
    val label: String get() = "%02d:%02d".format(hour, minute)
}

/**
 * Once a day at the chosen time: if today's todos aren't all ticked, Kaizen
 * says which ones out loud (and shows a notification). Exact alarm, like the
 * timers, so it rings on time with the app closed.
 */
object TodoReminder {

    private const val PREFS = "todo_reminder"
    private const val REQUEST_CODE = 42

    fun settings(context: Context): ReminderSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ReminderSettings(
            enabled = prefs.getBoolean("enabled", false),
            hour = prefs.getInt("hour", 20),
            minute = prefs.getInt("minute", 0),
        )
    }

    fun save(context: Context, settings: ReminderSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("hour", settings.hour)
            .putInt("minute", settings.minute)
            .apply()
        reschedule(context)
    }

    /** Sets (or clears) the alarm for the next occurrence. Safe to call any time. */
    fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        val settings = settings(context)
        if (!settings.enabled) return

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atTime(LocalTime.of(settings.hour, settings.minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(zone).toInstant().toEpochMilli()

        if (ExactAlarms.canSchedule(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, TodoReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as KaizenApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = TodoMath.reminderMessage(container.todoRepository.unfinishedToday())
                // Started from an exact alarm, so Android allows the foreground service.
                if (message != null) SpeakService.start(context, message)
                TodoReminder.reschedule(context) // tomorrow
            } finally {
                pending.finish()
            }
        }
    }
}
