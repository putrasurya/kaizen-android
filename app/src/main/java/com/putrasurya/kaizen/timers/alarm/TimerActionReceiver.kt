package com.putrasurya.kaizen.timers.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.putrasurya.kaizen.KaizenApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives a timer's alarm (time's up) and its notification buttons
 * (pause, resume, reset, skip break). Works even when the app was closed or killed:
 * Android starts the app process just for this.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (timerId < 0) return
        val repository = (context.applicationContext as KaizenApp).container.timerRepository

        // Database work can't run on the main thread; goAsync() gives us a few seconds.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FINISH -> repository.finish(timerId)
                    ACTION_PAUSE -> repository.pause(timerId)
                    ACTION_RESUME -> repository.start(timerId)
                    ACTION_STOP -> repository.stop(timerId)
                    ACTION_SKIP -> repository.skipBreak(timerId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FINISH = "com.putrasurya.kaizen.timer.FINISH"
        const val ACTION_PAUSE = "com.putrasurya.kaizen.timer.PAUSE"
        const val ACTION_RESUME = "com.putrasurya.kaizen.timer.RESUME"
        const val ACTION_STOP = "com.putrasurya.kaizen.timer.STOP"
        const val ACTION_SKIP = "com.putrasurya.kaizen.timer.SKIP_BREAK"
        private const val EXTRA_TIMER_ID = "timerId"

        fun intent(context: Context, action: String, timerId: Long): Intent =
            Intent(context, TimerActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_TIMER_ID, timerId)
    }
}
