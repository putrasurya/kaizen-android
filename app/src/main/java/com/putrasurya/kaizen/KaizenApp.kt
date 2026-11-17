package com.putrasurya.kaizen

import android.app.Application
import com.putrasurya.kaizen.core.AppContainer
import com.putrasurya.kaizen.reminder.Notifications

class KaizenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Channels must exist before any notification is shown.
        Notifications.createChannel(this)
    }
}
