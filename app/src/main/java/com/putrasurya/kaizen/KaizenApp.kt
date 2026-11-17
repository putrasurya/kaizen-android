package com.putrasurya.kaizen

import android.app.Application
import com.putrasurya.kaizen.core.AppContainer
import com.putrasurya.kaizen.reminder.Notifications
import com.putrasurya.kaizen.timers.alarm.TimerNotifications
import com.putrasurya.kaizen.todos.reminder.TodoReminder
import kotlinx.coroutines.launch

class KaizenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Channels must exist before any notification is shown.
        Notifications.createChannel(this)
        TimerNotifications.createChannels(this)
        // Tidy up timers whose alarm was missed. A minute of grace, because the
        // app may be starting right now *because* an alarm is firing.
        container.applicationScope.launch {
            container.timerRepository.restore(graceMs = 60_000)
        }
        // Cheap and safe to repeat: makes sure the evening reminder is set.
        TodoReminder.reschedule(this)
    }
}
