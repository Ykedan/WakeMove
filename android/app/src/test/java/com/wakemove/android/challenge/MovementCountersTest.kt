package com.wakemove.android.challenge

import org.junit.Assert.assertEquals
import org.junit.Test

class MovementCountersTest {
    @Test
    fun squat_counts_only_after_standing_bottom_and_standing_are_each_stable() {
        val counter = SquatCounter()

        assertEquals(0, feed(counter, standing(), 0L, 100L, 3).repetitions)
        assertEquals(0, feed(counter, squatBottom(), 300L, 100L, 3).repetitions)
        assertEquals(1, feed(counter, standing(), 600L, 100L, 3).repetitions)
    }

    @Test
    fun squat_does_not_count_an_incomplete_or_jittery_movement() {
        val incomplete = SquatCounter()
        feed(incomplete, standing(), 0L, 100L, 3)
        assertEquals(0, feed(incomplete, squatBottom(), 300L, 100L, 3).repetitions)

        val jittery = SquatCounter()
        feed(jittery, standing(), 0L, 100L, 3)
        jittery.update(frame(300L, squatBottom()))
        jittery.update(frame(400L, standing()))
        jittery.update(frame(500L, squatBottom()))
        jittery.update(frame(600L, squatBottom()))
        assertEquals(0, feed(jittery, standing(), 700L, 100L, 3).repetitions)
    }

    @Test
    fun squat_ignores_low_visibility_and_unrelated_movement() {
        val lowVisibility = SquatCounter()
        feed(lowVisibility, standing(), 0L, 100L, 3)
        assertEquals(
            0,
            feed(lowVisibility, squatBottom(visibility = 0.64f), 300L, 100L, 3).repetitions,
        )

        val minimumVisibility = SquatCounter()
        feed(minimumVisibility, standing(visibility = 0.65f), 0L, 100L, 3)
        feed(minimumVisibility, squatBottom(visibility = 0.65f), 300L, 100L, 3)
        assertEquals(
            1,
            feed(minimumVisibility, standing(visibility = 0.65f), 600L, 100L, 3).repetitions,
        )

        val unrelated = SquatCounter()
        assertEquals(0, feed(unrelated, jackOpen(), 0L, 100L, 9).repetitions)
    }

    @Test
    fun squat_enforces_a_350_ms_cooldown_between_repetitions() {
        val counter = SquatCounter()

        feed(counter, standing(), 0L, 10L, 3)
        feed(counter, squatBottom(), 30L, 10L, 3)
        assertEquals(1, feed(counter, standing(), 60L, 10L, 3).repetitions)
        feed(counter, squatBottom(), 90L, 10L, 3)
        assertEquals(1, feed(counter, standing(), 120L, 10L, 3).repetitions)
        feed(counter, squatBottom(), 440L, 10L, 3)
        assertEquals(2, feed(counter, standing(), 470L, 10L, 3).repetitions)
    }

    @Test
    fun jumping_jack_counts_only_after_closed_open_and_closed_are_each_stable() {
        val counter = JumpingJackCounter()

        feed(counter, jackClosed(), 0L, 100L, 3)
        feed(counter, jackOpen(), 300L, 100L, 3)
        assertEquals(1, feed(counter, jackClosed(), 600L, 100L, 3).repetitions)
    }

    @Test
    fun jumping_jack_ignores_jitter_low_visibility_and_squats() {
        val jittery = JumpingJackCounter()
        feed(jittery, jackClosed(), 0L, 100L, 3)
        jittery.update(frame(300L, jackOpen()))
        jittery.update(frame(400L, jackClosed()))
        jittery.update(frame(500L, jackOpen()))
        jittery.update(frame(600L, jackOpen()))
        assertEquals(0, feed(jittery, jackClosed(), 700L, 100L, 3).repetitions)

        val lowVisibility = JumpingJackCounter()
        feed(lowVisibility, jackClosed(), 0L, 100L, 3)
        feed(lowVisibility, jackOpen(visibility = 0.64f), 300L, 100L, 3)
        assertEquals(0, feed(lowVisibility, jackClosed(), 600L, 100L, 3).repetitions)

        val minimumVisibility = JumpingJackCounter()
        feed(minimumVisibility, jackClosed(visibility = 0.65f), 0L, 100L, 3)
        feed(minimumVisibility, jackOpen(visibility = 0.65f), 300L, 100L, 3)
        assertEquals(
            1,
            feed(minimumVisibility, jackClosed(visibility = 0.65f), 600L, 100L, 3).repetitions,
        )

        val unrelated = JumpingJackCounter()
        assertEquals(0, feed(unrelated, squatBottom(), 0L, 100L, 9).repetitions)
        feed(unrelated, jackOpen(), 900L, 100L, 3)
        assertEquals(0, feed(unrelated, jackClosed(), 1_200L, 100L, 3).repetitions)
        feed(unrelated, jackClosed(), 1_500L, 100L, 3)
        feed(unrelated, jackOpen(), 1_800L, 100L, 3)
        assertEquals(1, feed(unrelated, jackClosed(), 2_100L, 100L, 3).repetitions)
    }

    @Test
    fun hands_up_counts_once_after_both_wrists_stay_near_the_shoulders_for_1000_ms() {
        val counter = HandsUpCounter()

        feed(counter, handsNearShoulders(), 0L, 100L, 2)
        assertEquals(0, counter.update(frame(999L, handsNearShoulders())).repetitions)
        assertEquals(1, counter.update(frame(1_000L, handsNearShoulders())).repetitions)
        assertEquals(1, counter.update(frame(1_100L, handsNearShoulders())).repetitions)
    }

    @Test
    fun hands_up_ignores_low_visibility_jitter_and_one_raised_hand() {
        val lowVisibility = HandsUpCounter()
        feed(lowVisibility, handsUp(visibility = 0.49f), 0L, 100L, 3)
        assertEquals(0, lowVisibility.update(frame(1_100L, handsUp(visibility = 0.49f))).repetitions)

        val minimumVisibility = HandsUpCounter()
        feed(minimumVisibility, handsUp(visibility = 0.50f), 0L, 100L, 2)
        assertEquals(1, minimumVisibility.update(frame(1_000L, handsUp(visibility = 0.50f))).repetitions)

        val jittery = HandsUpCounter()
        jittery.update(frame(0L, handsUp()))
        jittery.update(frame(100L, handsDown()))
        feed(jittery, handsUp(), 200L, 100L, 2)
        assertEquals(0, jittery.update(frame(1_199L, handsUp())).repetitions)

        val unrelated = HandsUpCounter()
        assertEquals(0, feed(unrelated, oneHandUp(), 0L, 100L, 22).repetitions)
    }

    private fun feed(
        counter: MovementCounter,
        landmarks: Map<PoseLandmark, Landmark>,
        startTimestampMs: Long,
        frameIntervalMs: Long,
        frames: Int,
    ): ChallengeProgress {
        var progress = counter.update(frame(startTimestampMs, landmarks))
        repeat(frames - 1) { index ->
            progress = counter.update(frame(startTimestampMs + (index + 1) * frameIntervalMs, landmarks))
        }
        return progress
    }

    private fun frame(timestampMs: Long, landmarks: Map<PoseLandmark, Landmark>) =
        PoseFrame(timestampMs = timestampMs, landmarks = landmarks)

    private fun standing(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_HIP, 0.35f, 0.45f)
        set(PoseLandmark.RIGHT_HIP, 0.65f, 0.45f)
        set(PoseLandmark.LEFT_KNEE, 0.35f, 0.65f)
        set(PoseLandmark.RIGHT_KNEE, 0.65f, 0.65f)
        set(PoseLandmark.LEFT_ANKLE, 0.35f, 0.85f)
        set(PoseLandmark.RIGHT_ANKLE, 0.65f, 0.85f)
    }

    private fun squatBottom(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_HIP, 0.25f, 0.55f)
        set(PoseLandmark.RIGHT_HIP, 0.75f, 0.55f)
        set(PoseLandmark.LEFT_KNEE, 0.35f, 0.65f)
        set(PoseLandmark.RIGHT_KNEE, 0.65f, 0.65f)
        set(PoseLandmark.LEFT_ANKLE, 0.45f, 0.55f)
        set(PoseLandmark.RIGHT_ANKLE, 0.55f, 0.55f)
    }

    private fun jackClosed(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_WRIST, 0.30f, 0.75f)
        set(PoseLandmark.RIGHT_WRIST, 0.70f, 0.75f)
        set(PoseLandmark.LEFT_HIP, 0.42f, 0.55f)
        set(PoseLandmark.RIGHT_HIP, 0.58f, 0.55f)
        set(PoseLandmark.LEFT_KNEE, 0.42f, 0.65f)
        set(PoseLandmark.RIGHT_KNEE, 0.58f, 0.65f)
        set(PoseLandmark.LEFT_ANKLE, 0.42f, 0.85f)
        set(PoseLandmark.RIGHT_ANKLE, 0.58f, 0.85f)
    }

    private fun jackOpen(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_WRIST, 0.15f, 0.12f)
        set(PoseLandmark.RIGHT_WRIST, 0.85f, 0.12f)
        set(PoseLandmark.LEFT_HIP, 0.35f, 0.55f)
        set(PoseLandmark.RIGHT_HIP, 0.65f, 0.55f)
        set(PoseLandmark.LEFT_KNEE, 0.25f, 0.70f)
        set(PoseLandmark.RIGHT_KNEE, 0.75f, 0.70f)
        set(PoseLandmark.LEFT_ANKLE, 0.15f, 0.85f)
        set(PoseLandmark.RIGHT_ANKLE, 0.85f, 0.85f)
    }

    private fun handsUp(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_WRIST, 0.30f, 0.12f)
        set(PoseLandmark.RIGHT_WRIST, 0.70f, 0.12f)
    }

    private fun handsNearShoulders(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_WRIST, 0.30f, 0.37f)
        set(PoseLandmark.RIGHT_WRIST, 0.70f, 0.37f)
    }

    private fun handsDown(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_WRIST, 0.30f, 0.75f)
        set(PoseLandmark.RIGHT_WRIST, 0.70f, 0.75f)
    }

    private fun oneHandUp(visibility: Float = 1f) = pose(visibility) {
        set(PoseLandmark.LEFT_WRIST, 0.30f, 0.12f)
        set(PoseLandmark.RIGHT_WRIST, 0.70f, 0.75f)
    }

    private fun pose(
        visibility: Float,
        configure: MutableMap<PoseLandmark, Landmark>.() -> Unit,
    ): Map<PoseLandmark, Landmark> = buildMap {
        set(PoseLandmark.NOSE, Landmark(0.5f, 0.25f, visibility = visibility))
        set(PoseLandmark.LEFT_SHOULDER, Landmark(0.35f, 0.35f, visibility = visibility))
        set(PoseLandmark.RIGHT_SHOULDER, Landmark(0.65f, 0.35f, visibility = visibility))
        set(PoseLandmark.LEFT_HIP, Landmark(0.35f, 0.55f, visibility = visibility))
        set(PoseLandmark.RIGHT_HIP, Landmark(0.65f, 0.55f, visibility = visibility))
        set(PoseLandmark.LEFT_KNEE, Landmark(0.35f, 0.65f, visibility = visibility))
        set(PoseLandmark.RIGHT_KNEE, Landmark(0.65f, 0.65f, visibility = visibility))
        set(PoseLandmark.LEFT_ANKLE, Landmark(0.35f, 0.85f, visibility = visibility))
        set(PoseLandmark.RIGHT_ANKLE, Landmark(0.65f, 0.85f, visibility = visibility))
        set(PoseLandmark.LEFT_WRIST, Landmark(0.30f, 0.75f, visibility = visibility))
        set(PoseLandmark.RIGHT_WRIST, Landmark(0.70f, 0.75f, visibility = visibility))
        configure()
    }

    private fun MutableMap<PoseLandmark, Landmark>.set(landmark: PoseLandmark, x: Float, y: Float) {
        this[landmark] = Landmark(x, y, visibility = getValue(landmark).visibility)
    }
}
