package org.ward.adhkar.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Prefers a local Arabic TTS voice; never intentionally uses a network voice. */
class WardSpeech(context: Context) : TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var released = false

    var ready by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var speaking by mutableStateOf(false)
        private set

    init { tts = TextToSpeech(context.applicationContext, this) }

    override fun onInit(status: Int) {
        handler.post {
            if (released) return@post
            loading = false
            val engine = tts ?: return@post
            if (status != TextToSpeech.SUCCESS) return@post
            val arabic = Locale.forLanguageTag("ar")
            if (engine.isLanguageAvailable(arabic) < TextToSpeech.LANG_AVAILABLE) return@post
            val offline = engine.voices?.filter {
                it.locale.language == "ar" && !it.isNetworkConnectionRequired
            }?.maxByOrNull { it.quality } ?: return@post
            ready = engine.setVoice(offline) == TextToSpeech.SUCCESS
            if (!ready) ready = engine.setLanguage(arabic) >= TextToSpeech.LANG_AVAILABLE &&
                engine.voice?.isNetworkConnectionRequired == false
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    handler.post { if (!released) speaking = true }
                }
                override fun onDone(utteranceId: String?) {
                    handler.post { if (!released) speaking = false }
                }
                @Deprecated("Android TTS callback")
                override fun onError(utteranceId: String?) {
                    handler.post { if (!released) speaking = false }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    handler.post { if (!released) speaking = false }
                }
            })
        }
    }

    fun speak(text: String, rate: Float) {
        if (!ready || released) return
        tts?.setSpeechRate(rate.coerceIn(0.6f, 1.25f))
        speaking = true
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ward-dhikr")
        if (result == TextToSpeech.ERROR) speaking = false
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }

    fun release() {
        released = true
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

/** Optional short nonverbal feedback that respects media volume. */
class WardSounds {
    private val tone: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 18)
    }.getOrNull()

    fun play(completed: Boolean) {
        runCatching {
            tone?.startTone(
                if (completed) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                if (completed) 100 else 45
            )
        }
    }

    fun release() { tone?.release() }
}
