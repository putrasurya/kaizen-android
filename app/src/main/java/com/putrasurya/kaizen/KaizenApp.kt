package com.putrasurya.kaizen

import android.app.Application
import com.putrasurya.kaizen.reminder.Notifications

class KaizenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Channels must exist before any notification is shown.
        Notifications.createChannel(this)
    }
}
