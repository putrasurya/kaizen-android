package com.putrasurya.kaizen.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.putrasurya.kaizen.KaizenApp
import com.putrasurya.kaizen.todos.reminder.TodoReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android forgets every alarm when the phone restarts (and when the app is
 * updated). This puts them back. Later features add their own restore calls here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val container = (context.applicationContext as KaizenApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.timerRepository.restore(graceMs = 0)
                TodoReminder.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
