package com.putrasurya.kaizen.core

import android.content.Context
import com.putrasurya.kaizen.core.data.KaizenDatabase
import com.putrasurya.kaizen.timers.alarm.TimerAlarms
import com.putrasurya.kaizen.timers.alarm.TimerNotifications
import com.putrasurya.kaizen.timers.data.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/**
 * Creates the app's shared objects by hand, in one place (ADR-003).
 * Screens get what they need from here instead of creating it themselves,
 * so tests can pass in fakes (for example a fixed [clock]).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** "Now" for the whole app. Tests use Clock.fixed(...) instead. */
    val clock: Clock = Clock.systemDefaultZone()

    /** For work that should outlive a single screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: KaizenDatabase by lazy { KaizenDatabase.create(appContext) }

    val timerRepository: TimerRepository by lazy {
        TimerRepository(
            dao = database.timerDao(),
            alarms = TimerAlarms(appContext),
            notifications = TimerNotifications(appContext),
            clock = clock,
        )
    }
}
