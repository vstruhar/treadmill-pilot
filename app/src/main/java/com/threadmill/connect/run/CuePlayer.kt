package com.threadmill.connect.run

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

class CuePlayer(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val tones = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    private var ready = false
    private var speech: TextToSpeech? = null

    init {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speech?.language = Locale.US
                ready = true
            }
        }
        speech = engine
        if (ready) engine.language = Locale.US
    }
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .build()

    fun speak(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audio?.requestAudioFocus(focus)
        }
        if (ready) speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue")
    }

    fun beep() {
        tones.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
    }
}
