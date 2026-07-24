package com.wakemove.android.ringing

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import com.wakemove.android.R
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAlarmAudioPlayerTest {
    @After
    fun tearDown() {
        ShadowMediaPlayer.resetStaticState()
    }

    @Test
    fun `play loops the bundled alarm with alarm audio attributes`() {
        var createdPlayer: MediaPlayer? = null
        ShadowMediaPlayer.setMediaInfoProvider {
            ShadowMediaPlayer.MediaInfo(DEFAULT_DURATION_MILLIS, 0)
        }
        ShadowMediaPlayer.setCreateListener { player, _ -> createdPlayer = player }
        val audioPlayer = AndroidAlarmAudioPlayer(RuntimeEnvironment.getApplication())

        audioPlayer.play("default")

        val mediaPlayer = checkNotNull(createdPlayer)
        val shadowPlayer = shadowOf(mediaPlayer)
        assertEquals(AlarmSoundState.PLAYING, audioPlayer.soundState)
        assertEquals(R.raw.default_alarm, shadowPlayer.sourceResId)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowPlayer.audioAttributes.usage)
        assertTrue(mediaPlayer.isLooping)
        assertTrue(mediaPlayer.isPlaying)

        audioPlayer.stop()

        assertEquals(AlarmSoundState.STOPPED, audioPlayer.soundState)
        assertEquals(ShadowMediaPlayer.State.END, shadowPlayer.state)
    }

    @Test
    fun `content sound id plays the persisted uri`() {
        val uri = Uri.parse("content://media/external/audio/media/42")
        val players = capturePlayersWithMediaInfo()
        val audioPlayer = AndroidAlarmAudioPlayer(RuntimeEnvironment.getApplication())

        audioPlayer.play(uri.toString())

        val shadowPlayer = shadowOf(players.single())
        assertEquals(AlarmSoundState.PLAYING, audioPlayer.soundState)
        assertEquals(uri, shadowPlayer.sourceUri)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowPlayer.audioAttributes.usage)
    }

    @Test
    fun `invalid sound id deterministically falls back to bundled alarm`() {
        val players = capturePlayersWithMediaInfo()
        val audioPlayer = AndroidAlarmAudioPlayer(RuntimeEnvironment.getApplication())

        audioPlayer.play("file:///untrusted/alarm.ogg")

        assertEquals(AlarmSoundState.PLAYING, audioPlayer.soundState)
        assertEquals(R.raw.default_alarm, shadowOf(players.single()).sourceResId)
    }

    @Test
    fun `unavailable content uri falls back to bundled alarm`() {
        val context: android.app.Application = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://media/external/audio/media/missing")
        val players = capturePlayersWithMediaInfo()
        ShadowMediaPlayer.addException(
            DataSource.toDataSource(context, uri),
            IOException("missing"),
        )
        val audioPlayer = AndroidAlarmAudioPlayer(context)

        audioPlayer.play(uri.toString())

        assertEquals(AlarmSoundState.PLAYING, audioPlayer.soundState)
        assertEquals(2, players.size)
        assertEquals(R.raw.default_alarm, shadowOf(players.last()).sourceResId)
    }

    @Test
    fun `audio focus denial releases player and reports failed`() {
        val context: android.app.Application = RuntimeEnvironment.getApplication()
        val players = capturePlayersWithMediaInfo()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = shadowOf(audioManager)
        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val audioPlayer = AndroidAlarmAudioPlayer(context)

        audioPlayer.play("default")

        assertEquals(AlarmSoundState.FAILED, audioPlayer.soundState)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.single()).state)
        assertNotNull(shadowAudioManager.lastAudioFocusRequest)
    }

    @Test
    fun `player creation failure reports failed without requesting focus`() {
        val context: android.app.Application = RuntimeEnvironment.getApplication()
        ShadowMediaPlayer.setMediaInfoProvider { null }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = shadowOf(audioManager)
        val audioPlayer = AndroidAlarmAudioPlayer(context)

        audioPlayer.play("default")

        assertEquals(AlarmSoundState.FAILED, audioPlayer.soundState)
        assertNull(shadowAudioManager.lastAudioFocusRequest)
    }

    private fun capturePlayersWithMediaInfo(): MutableList<MediaPlayer> {
        val players = mutableListOf<MediaPlayer>()
        ShadowMediaPlayer.setMediaInfoProvider {
            ShadowMediaPlayer.MediaInfo(DEFAULT_DURATION_MILLIS, 0)
        }
        ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
        return players
    }

    private companion object {
        const val DEFAULT_DURATION_MILLIS = 4_000
    }
}
