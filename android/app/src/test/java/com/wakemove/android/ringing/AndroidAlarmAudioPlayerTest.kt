package com.wakemove.android.ringing

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.wakemove.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer

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

    private companion object {
        const val DEFAULT_DURATION_MILLIS = 4_000
    }
}
