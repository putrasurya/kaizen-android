package com.putrasurya.kaizen.core

import android.content.Context
import com.putrasurya.kaizen.core.data.KaizenDatabase
import java.time.Clock

/**
 * Creates the app's shared objects by hand, in one place (ADR-003).
 * Screens get what they need from here instead of creating it themselves,
 * so tests can pass in fakes (for example a fixed [clock]).
 */
class AppContainer(context: Context) {

    /** "Now" for the whole app. Tests use Clock.fixed(...) instead. */
    val clock: Clock = Clock.systemDefaultZone()

    val database: KaizenDatabase by lazy { KaizenDatabase.create(context) }
}
