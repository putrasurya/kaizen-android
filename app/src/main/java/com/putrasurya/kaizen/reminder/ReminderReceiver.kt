package com.putrasurya.kaizen.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android calls onReceive() when the alarm time arrives.
 * Later this is where Kaizen will read unfinished tasks and ask the AI
 * (through your backend) for the message. For now the message is passed in.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: DEFAULT_MESSAGE
        // Starting a foreground service from the background is normally blocked
        // on Android 12+, but an exact alarm is one of the allowed exceptions.
        SpeakService.start(context, message)
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
        const val DEFAULT_MESSAGE =
            "Kamu masih punya tugas yang belum selesai. Kalau tidak sempat, catat di jurnal ya."
    }
}
