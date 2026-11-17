package com.putrasurya.kaizen.timers.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.net.Uri
import com.putrasurya.kaizen.MainActivity
import com.putrasurya.kaizen.R
import com.putrasurya.kaizen.timers.data.TimerEntity
import com.putrasurya.kaizen.timers.data.TimerPhase
import com.putrasurya.kaizen.timers.domain.TimerMath

/**
 * The notifications of a timer. Each timer has one notification slot that
 * changes between running, paused, round done and break over.
 */
class TimerNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Countdown drawn by Android itself, so the app never updates it every second. */
    fun showRunning(timer: TimerEntity, phase: TimerPhase, endsAt: Long) {
        val builder = base(RUNNING_CHANNEL, timer)
            .setContentText(phaseLabel(phase))
            .withCountdown(endsAt)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action("Pause", TimerActionReceiver.ACTION_PAUSE, timer.id))
        addEndAction(builder, phase, timer.id)
        notify(timer.id, builder.build())
    }

    fun showPaused(timer: TimerEntity, phase: TimerPhase, remainingMs: Long) {
        val left = TimerMath.formatClock(TimerMath.displaySeconds(remainingMs))
        val builder = base(RUNNING_CHANNEL, timer)
            .setContentText("${phaseLabel(phase)} paused · $left left")
            .setShowWhen(false)
            .addAction(action("Resume", TimerActionReceiver.ACTION_RESUME, timer.id))
        addEndAction(builder, phase, timer.id)
        notify(timer.id, builder.build())
    }

    /**
     * Time's up for a focus round (plays the sound). With Pomodoro, the break
     * that follows is shown in the same notification: counting down if it
     * started by itself ([breakEndsAt]), or ready to start.
     */
    fun showRoundDone(
        timer: TimerEntity,
        repsToday: Int,
        breakPhase: TimerPhase? = null,
        breakEndsAt: Long? = null,
    ) {
        val reps = "${TimerMath.repsLabel(repsToday)} today"
        val builder = base(DONE_CHANNEL, timer)
            .setCategory(Notification.CATEGORY_ALARM)
        when {
            breakPhase == null -> builder
                .setContentText("Time's up · $reps")
                .setAutoCancel(true)
            breakEndsAt != null -> builder
                .setContentText("Round done · $reps · ${phaseLabel(breakPhase)}")
                .withCountdown(breakEndsAt)
                .setOngoing(true)
                .addAction(action("Pause", TimerActionReceiver.ACTION_PAUSE, timer.id))
                .addAction(action("Skip break", TimerActionReceiver.ACTION_SKIP, timer.id))
            else -> builder
                .setContentText("Round done · $reps · ${phaseLabel(breakPhase)} ready")
                .addAction(action("Start break", TimerActionReceiver.ACTION_RESUME, timer.id))
                .addAction(action("Skip break", TimerActionReceiver.ACTION_SKIP, timer.id))
        }
        notify(timer.id, builder.build())
    }

    /** A break reached zero (plays the sound): the next focus round waits for Start. */
    fun showBreakOver(timer: TimerEntity) {
        val notification = base(DONE_CHANNEL, timer)
            .setContentText("Break over · ready for the next round")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notify(timer.id, notification)
    }

    fun cancel(timerId: Long) {
        manager.cancel(notificationId(timerId))
    }

    private fun addEndAction(builder: Notification.Builder, phase: TimerPhase, timerId: Long) {
        if (phase == TimerPhase.FOCUS) {
            builder.addAction(action("Reset", TimerActionReceiver.ACTION_STOP, timerId))
        } else {
            builder.addAction(action("Skip break", TimerActionReceiver.ACTION_SKIP, timerId))
        }
    }

    private fun Notification.Builder.withCountdown(endsAt: Long): Notification.Builder =
        setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAt)
            .setShowWhen(true)

    private fun notify(timerId: Long, notification: Notification) {
        // Without the notification permission this would be dropped anyway.
        if (manager.areNotificationsEnabled()) {
            manager.notify(notificationId(timerId), notification)
        }
    }

    private fun base(channel: String, timer: TimerEntity): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(timer.title)
            .setContentIntent(openApp())

    private fun action(label: String, action: String, timerId: Long): Notification.Action {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timerId.toInt(),
            TimerActionReceiver.intent(context, action, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(context, android.R.drawable.ic_lock_idle_alarm)
        return Notification.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val RUNNING_CHANNEL = "timer_running"
        // A channel's sound can't change after it is created; a new sound needs a new id.
        private const val DONE_CHANNEL = "timer_done_v1"
        private const val ID_OFFSET = 1_000

        fun notificationId(timerId: Long): Int = ID_OFFSET + timerId.toInt()

        fun phaseLabel(phase: TimerPhase): String = when (phase) {
            TimerPhase.FOCUS -> "Focus"
            TimerPhase.BREAK -> "Break"
            TimerPhase.LONG_BREAK -> "Long break"
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val running = NotificationChannel(
                RUNNING_CHANNEL, "Running timers", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Countdown of timers that are running or paused" }

            val sound = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .appendPath(R.raw.timer_done.toString())
                .build()
            val done = NotificationChannel(
                DONE_CHANNEL, "Timer finished", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Plays a sound when a timer or break reaches zero"
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            }
            manager.createNotificationChannels(listOf(running, done))
        }
    }
}
