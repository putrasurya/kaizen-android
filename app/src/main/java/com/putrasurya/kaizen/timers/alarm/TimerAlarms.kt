package com.putrasurya.kaizen.timers.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.putrasurya.kaizen.core.system.ExactAlarms

/** One exact alarm per running timer, firing at its end time (ADR-005). */
class TimerAlarms(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timerId: Long, endsAt: Long) {
        val pendingIntent = finishIntent(timerId)
        if (ExactAlarms.canSchedule(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pendingIntent)
        } else {
            // Without "Alarms & reminders" permission: may arrive a few minutes late.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pendingIntent)
        }
    }

    fun cancel(timerId: Long) {
        alarmManager.cancel(finishIntent(timerId))
    }

    private fun finishIntent(timerId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        timerId.toInt(),
        TimerActionReceiver.intent(context, TimerActionReceiver.ACTION_FINISH, timerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
