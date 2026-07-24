package com.wakemove.android.ringing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.net.toUri
import com.wakemove.android.R

enum class AlarmSoundState {
    STOPPED,
    PLAYING,
    FAILED,
}

interface AlarmAudioPlayer {
    val soundState: AlarmSoundState

    fun play(soundId: String)

    fun stop()
}

class AndroidAlarmAudioPlayer(
    context: Context,
    private val audioManager: AudioManager =
        context.getSystemService(AudioManager::class.java),
) : AlarmAudioPlayer {
    private val appContext = context.applicationContext
    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    override var soundState: AlarmSoundState = AlarmSoundState.STOPPED
        private set

    @Synchronized
    override fun play(soundId: String) {
        stop()
        val player = try {
            createPlayer(soundId)
        } catch (_: RuntimeException) {
            null
        }
        if (player == null) {
            soundState = AlarmSoundState.FAILED
            return
        }
        mediaPlayer = player
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(alarmAttributes)
                .setOnAudioFocusChangeListener { }
                .build()
            if (audioManager.requestAudioFocus(request) !=
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ) {
                releasePlayer()
                soundState = AlarmSoundState.FAILED
                return
            }
            focusRequest = request
            player.isLooping = true
            player.start()
            soundState = AlarmSoundState.PLAYING
        } catch (_: RuntimeException) {
            releasePlayer()
            soundState = AlarmSoundState.FAILED
        }
    }

    private fun createPlayer(soundId: String): MediaPlayer? {
        if (soundId == DEFAULT_SOUND_ID) return createDefaultPlayer()
        val uri = soundId.toUri()
        if (uri.scheme.equals(CONTENT_SCHEME, ignoreCase = true)) {
            createContentPlayer(uri)?.let { return it }
        }
        return createDefaultPlayer()
    }

    private fun createContentPlayer(uri: Uri): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(alarmAttributes)
            player.setDataSource(appContext, uri)
            player.prepare()
            player
        } catch (_: Exception) {
            player.release()
            null
        }
    }

    private fun createDefaultPlayer(): MediaPlayer? =
        MediaPlayer.create(
            appContext,
            R.raw.default_alarm,
            alarmAttributes,
            AUDIO_SESSION_ID_NONE,
        )

    @Synchronized
    override fun stop() {
        releasePlayer()
        soundState = AlarmSoundState.STOPPED
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private companion object {
        const val DEFAULT_SOUND_ID = "default"
        const val CONTENT_SCHEME = "content"
        const val AUDIO_SESSION_ID_NONE = 0
    }
}
