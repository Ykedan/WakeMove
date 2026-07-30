package com.wakemove.android.ringing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.RawRes
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

data class AlarmSound(
    val id: String,
    val name: String,
    val description: String,
    @param:RawRes val resourceId: Int,
    val waveform: List<Float>,
)

object AlarmSoundCatalog {
    const val DEFAULT_ID = "dawn_breeze"

    val sounds = listOf(
        AlarmSound(
            id = DEFAULT_ID,
            name = "晨风",
            description = "柔和木琴与暖色和弦",
            resourceId = R.raw.dawn_breeze,
            waveform = listOf(0.28f, 0.58f, 0.84f, 0.46f, 0.72f, 0.38f),
        ),
        AlarmSound(
            id = "sunrise_chimes",
            name = "朝露",
            description = "清亮铃音，缓慢渐醒",
            resourceId = R.raw.sunrise_chimes,
            waveform = listOf(0.42f, 0.88f, 0.34f, 0.68f, 0.92f, 0.52f),
        ),
        AlarmSound(
            id = "quiet_harbor",
            name = "静港",
            description = "低柔音垫与舒缓钟声",
            resourceId = R.raw.quiet_harbor,
            waveform = listOf(0.62f, 0.38f, 0.54f, 0.30f, 0.48f, 0.26f),
        ),
        AlarmSound(
            id = "forest_light",
            name = "林间光",
            description = "轻快音符与自然留白",
            resourceId = R.raw.forest_light,
            waveform = listOf(0.32f, 0.74f, 0.48f, 0.90f, 0.44f, 0.66f),
        ),
    )

    fun find(soundId: String): AlarmSound =
        sounds.firstOrNull { it.id == soundId }
            ?: sounds.first()
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
        val uri = soundId.toUri()
        if (uri.scheme.equals(CONTENT_SCHEME, ignoreCase = true)) {
            createContentPlayer(uri)?.let { return it }
        }
        return createBundledPlayer(soundId)
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

    private fun createBundledPlayer(soundId: String): MediaPlayer? =
        MediaPlayer.create(
            appContext,
            AlarmSoundCatalog.find(soundId).resourceId,
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
        const val CONTENT_SCHEME = "content"
        const val AUDIO_SESSION_ID_NONE = 0
    }
}

class AndroidAlarmSoundPreviewPlayer(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val previewAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var mediaPlayer: MediaPlayer? = null
    var playingSoundId: String? = null
        private set

    @Synchronized
    fun toggle(soundId: String, onCompleted: () -> Unit = {}): Boolean {
        if (playingSoundId == soundId) {
            stop()
            return false
        }
        stop()
        val sound = AlarmSoundCatalog.find(soundId)
        val player = runCatching {
            MediaPlayer.create(
                appContext,
                sound.resourceId,
                previewAttributes,
                AUDIO_SESSION_ID_NONE,
            )
        }.getOrNull() ?: return false
        mediaPlayer = player
        playingSoundId = sound.id
        player.isLooping = false
        player.setOnCompletionListener {
            stop()
            onCompleted()
        }
        if (runCatching { player.start() }.isFailure) {
            stop()
            return false
        }
        return true
    }

    @Synchronized
    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        playingSoundId = null
    }

    override fun close() = stop()

    private companion object {
        const val AUDIO_SESSION_ID_NONE = 0
    }
}
