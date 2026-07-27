package com.wakemove.android.challenge

import com.wakemove.android.domain.ChallengeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraChallengeControllerTest {
    @Test
    fun camera_starts_only_on_start_and_completes_at_the_requested_target() {
        val source = FakePoseLandmarkSource()
        val controller = CameraChallengeController(source, FakeChallengeDelayScheduler())

        assertFalse(source.started)

        controller.start(ChallengeType.SQUAT, targetCount = 1)
        assertTrue(source.started)
        source.emitStable(standing(), startTimestampMs = 0L)
        source.emitStable(squatBottom(), startTimestampMs = 300L)
        source.emitStable(standing(), startTimestampMs = 600L)

        assertEquals(
            ChallengeProgress(
                repetitions = 1,
                targetCount = 1,
                completed = true,
                guidance = CameraGuidance.NONE,
                fallbackAvailable = false,
            ),
            controller.progress.value,
        )
    }

    @Test
    fun reports_no_person_and_low_light_guidance_until_a_pose_is_detected() {
        val source = FakePoseLandmarkSource()
        val controller = CameraChallengeController(source, FakeChallengeDelayScheduler())
        controller.start(ChallengeType.HANDS_UP, targetCount = 1)

        source.emit(PoseObservation.NoPerson)
        assertEquals(CameraGuidance.NO_PERSON, controller.progress.value.guidance)

        source.emit(PoseObservation.LowLight)
        assertEquals(CameraGuidance.LOW_LIGHT, controller.progress.value.guidance)

        source.emit(PoseObservation.Frame(PoseFrame(0L, handsDown())))
        assertEquals(CameraGuidance.NONE, controller.progress.value.guidance)
    }

    @Test
    fun makes_fallback_available_after_sixty_seconds() {
        val scheduler = FakeChallengeDelayScheduler()
        val controller = CameraChallengeController(FakePoseLandmarkSource(), scheduler)
        controller.start(ChallengeType.JUMPING_JACK, targetCount = 3)

        scheduler.advanceBy(59_999L)
        assertFalse(controller.progress.value.fallbackAvailable)

        scheduler.advanceBy(1L)
        assertTrue(controller.progress.value.fallbackAvailable)
    }

    @Test
    fun close_releases_the_landmark_source_and_cancels_the_fallback_timer() {
        val source = FakePoseLandmarkSource()
        val scheduler = FakeChallengeDelayScheduler()
        val controller = CameraChallengeController(source, scheduler)
        controller.start(ChallengeType.SQUAT, targetCount = 1)

        controller.close()
        scheduler.advanceBy(60_000L)

        assertTrue(source.closed)
        assertFalse(controller.progress.value.fallbackAvailable)
    }

    @Test
    fun close_during_start_cancels_a_timer_returned_after_close() {
        val source = FakePoseLandmarkSource()
        val scheduler = FakeChallengeDelayScheduler()
        lateinit var controller: CameraChallengeController
        controller = CameraChallengeController(source, scheduler)
        scheduler.onSchedule = controller::close

        controller.start(ChallengeType.SQUAT, targetCount = 1)

        assertTrue(source.closed)
        assertTrue(scheduler.allTasksCancelled)
    }

    @Test
    fun adapter_frame_cleanup_runs_when_inference_throws() {
        val frame = CloseTrackingFrame()

        try {
            analyzeAndClose(frame) {
                error("inference failed")
            }
        } catch (_: IllegalStateException) {
            // The adapter must still release the camera frame.
        }

        assertTrue(frame.closed)
    }

    @Test
    fun adapter_identifies_dark_luma_but_accepts_a_well_lit_frame() {
        assertTrue(isLowLight(byteArrayOf(10, 20, 30, 40)))
        assertFalse(isLowLight(byteArrayOf(60, 70, 80, 90)))
    }

    @Test
    fun adapter_reports_no_person_instead_of_throwing_when_platform_startup_fails() {
        var observation: PoseObservation? = null

        val result = runAdapterOperation(
            onUnavailable = { observation = PoseObservation.NoPerson },
        ) {
            error("front camera unavailable")
        }

        assertNull(result)
        assertEquals(PoseObservation.NoPerson, observation)
    }

    private fun FakePoseLandmarkSource.emitStable(
        landmarks: Map<PoseLandmark, Landmark>,
        startTimestampMs: Long,
    ) {
        repeat(3) { index ->
            emit(PoseObservation.Frame(PoseFrame(startTimestampMs + index * 100L, landmarks)))
        }
    }

    private fun standing() = basePose().apply {
        set(PoseLandmark.LEFT_HIP, 0.35f, 0.45f)
        set(PoseLandmark.RIGHT_HIP, 0.65f, 0.45f)
        set(PoseLandmark.LEFT_KNEE, 0.35f, 0.65f)
        set(PoseLandmark.RIGHT_KNEE, 0.65f, 0.65f)
        set(PoseLandmark.LEFT_ANKLE, 0.35f, 0.85f)
        set(PoseLandmark.RIGHT_ANKLE, 0.65f, 0.85f)
    }

    private fun squatBottom() = basePose().apply {
        set(PoseLandmark.LEFT_HIP, 0.25f, 0.55f)
        set(PoseLandmark.RIGHT_HIP, 0.75f, 0.55f)
        set(PoseLandmark.LEFT_KNEE, 0.35f, 0.65f)
        set(PoseLandmark.RIGHT_KNEE, 0.65f, 0.65f)
        set(PoseLandmark.LEFT_ANKLE, 0.45f, 0.55f)
        set(PoseLandmark.RIGHT_ANKLE, 0.55f, 0.55f)
    }

    private fun handsDown() = basePose()

    private fun basePose() = PoseLandmark.entries.associateWithTo(mutableMapOf()) {
        Landmark(x = 0.5f, y = 0.5f)
    }

    private fun MutableMap<PoseLandmark, Landmark>.set(
        landmark: PoseLandmark,
        x: Float,
        y: Float,
    ) {
        this[landmark] = Landmark(x = x, y = y)
    }
}

private class CloseTrackingFrame : AutoCloseable {
    var closed = false
        private set

    override fun close() {
        closed = true
    }
}

private class FakePoseLandmarkSource : PoseLandmarkSource {
    var started = false
        private set
    var closed = false
        private set
    private var listener: ((PoseObservation) -> Unit)? = null

    override fun start(listener: (PoseObservation) -> Unit) {
        check(!closed)
        started = true
        this.listener = listener
    }

    fun emit(observation: PoseObservation) {
        check(started)
        listener?.invoke(observation)
    }

    override fun close() {
        closed = true
        listener = null
    }
}

private class FakeChallengeDelayScheduler : ChallengeDelayScheduler {
    private var nowMs = 0L
    private val tasks = mutableListOf<ScheduledTask>()
    var onSchedule: () -> Unit = {}

    val allTasksCancelled: Boolean
        get() = tasks.all(ScheduledTask::cancelled)

    override fun schedule(delayMs: Long, task: () -> Unit): AutoCloseable {
        val scheduled = ScheduledTask(runAtMs = nowMs + delayMs, task = task)
        tasks += scheduled
        onSchedule()
        return AutoCloseable { scheduled.cancelled = true }
    }

    fun advanceBy(durationMs: Long) {
        nowMs += durationMs
        tasks
            .filter { !it.cancelled && !it.executed && it.runAtMs <= nowMs }
            .forEach {
                it.executed = true
                it.task()
            }
    }

    private data class ScheduledTask(
        val runAtMs: Long,
        val task: () -> Unit,
        var cancelled: Boolean = false,
        var executed: Boolean = false,
    )
}
