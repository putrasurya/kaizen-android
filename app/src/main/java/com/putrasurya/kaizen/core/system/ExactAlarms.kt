package com.putrasurya.kaizen.core.system

import android.app.AlarmManager
import android.content.Context
import android.os.Build

object ExactAlarms {
    /** True when the app may set exact alarms ("Alarms & reminders" on Android 12+). */
    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}
