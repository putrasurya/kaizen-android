package com.putrasurya.kaizen.reminder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ServiceCompat
import java.util.Locale

/**
 * A short-lived foreground service:
 *  1. shows the reminder notification (it stays after the service stops),
 *  2. speaks the message with TextToSpeech, unless the phone is on silent/vibrate,
 *  3. stops itself when speaking is done.
 */
class SpeakService : Service() {

    private var tts: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra(EXTRA_MESSAGE)
            ?: ReminderReceiver.DEFAULT_MESSAGE

        // Must be called within a few seconds of starting a foreground service.
        ServiceCompat.startForeground(
            this,
            Notifications.REMINDER_ID,
            Notifications.reminder(this, message),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        // Respect the user: on silent or vibrate, only show the notification.
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            finish()
            return START_NOT_STICKY
        }

        // Safety net: never keep running longer than a minute.
        mainHandler.postDelayed({ finish() }, 60_000)

        speak(message)
        return START_NOT_STICKY
    }

    private fun speak(message: String) {
        tts?.shutdown()
        tts = TextToSpeech(this) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                finish()
                return@TextToSpeech
            }

            // Prefer Indonesian; fall back to the phone's own language.
            val result = engine.setLanguage(Locale.forLanguageTag("id-ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.getDefault())
            }
            engine.setAudioAttributes(audioAttributes)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { finish() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { finish() }
                }
            })

            requestAudioFocus()
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
    }

    /** Lowers ("ducks") music or podcasts while Kaizen is talking. */
    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun finish() {
        mainHandler.removeCallbacksAndMessages(null)
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        tts?.shutdown()
        tts = null
        // DETACH keeps the reminder notification visible after the service ends.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MESSAGE = "message"
        private const val UTTERANCE_ID = "kaizen-reminder"

        fun start(context: Context, message: String) {
            val intent = Intent(context, SpeakService::class.java)
                .putExtra(EXTRA_MESSAGE, message)
            context.startForegroundService(intent)
        }
    }
}
