package com.wakemove.android.ringing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(alarmAttributes)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)

            val player = checkNotNull(
                MediaPlayer.create(
                    appContext,
                    R.raw.default_alarm,
                    alarmAttributes,
                    AUDIO_SESSION_ID_NONE,
                ),
            ) { "Unable to open alarm sound '$soundId'" }
            mediaPlayer = player
            player.isLooping = true
            player.start()
            soundState = AlarmSoundState.PLAYING
        } catch (_: RuntimeException) {
            releasePlayer()
            soundState = AlarmSoundState.FAILED
        }
    }

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
        const val AUDIO_SESSION_ID_NONE = 0
    }
}
