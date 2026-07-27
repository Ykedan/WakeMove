package com.wakemove.android.challenge

import android.os.Handler
import android.os.Looper
import com.wakemove.android.domain.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PoseLandmarkSource : AutoCloseable {
    fun start(listener: (PoseObservation) -> Unit)
}

fun interface ChallengeDelayScheduler {
    fun schedule(delayMs: Long, task: () -> Unit): AutoCloseable
}

class CameraChallengeController(
    private val landmarkSource: PoseLandmarkSource,
    private val delayScheduler: ChallengeDelayScheduler = HandlerChallengeDelayScheduler(),
) : AutoCloseable {
    private val mutableProgress = MutableStateFlow(ChallengeProgress(repetitions = 0))
    private val lock = Any()
    private var counter: MovementCounter? = null
    private var targetCount = 0
    private var fallbackTimer: AutoCloseable? = null
    private var started = false
    private var closed = false

    val progress: StateFlow<ChallengeProgress> = mutableProgress.asStateFlow()

    fun start(type: ChallengeType, targetCount: Int) {
        require(targetCount > 0) { "targetCount must be positive" }
        val selectedCounter = when (type) {
            ChallengeType.SQUAT -> SquatCounter()
            ChallengeType.JUMPING_JACK -> JumpingJackCounter()
            ChallengeType.HANDS_UP -> HandsUpCounter()
            ChallengeType.VOICE_PHRASE -> error("VOICE_PHRASE is not a camera challenge")
        }

        synchronized(lock) {
            check(!closed) { "CameraChallengeController is closed" }
            check(!started) { "CameraChallengeController is already started" }
            started = true
            counter = selectedCounter
            this.targetCount = targetCount
            mutableProgress.value = ChallengeProgress(
                repetitions = 0,
                targetCount = targetCount,
                guidance = CameraGuidance.NO_PERSON,
            )
            try {
                landmarkSource.start(::onObservation)
                val timer = delayScheduler.schedule(FALLBACK_DELAY_MS) {
                    synchronized(lock) {
                        if (!closed) {
                            mutableProgress.value =
                                mutableProgress.value.copy(fallbackAvailable = true)
                        }
                    }
                }
                if (closed) timer.close() else fallbackTimer = timer
            } catch (error: Throwable) {
                closed = true
                landmarkSource.close()
                throw error
            }
        }
    }

    override fun close() {
        val timer = synchronized(lock) {
            if (closed) return
            closed = true
            fallbackTimer.also { fallbackTimer = null }
        }
        timer?.close()
        landmarkSource.close()
    }

    private fun onObservation(observation: PoseObservation) {
        synchronized(lock) {
            if (closed || !started) return
            when (observation) {
                PoseObservation.LowLight -> updateGuidance(CameraGuidance.LOW_LIGHT)
                PoseObservation.NoPerson -> updateGuidance(CameraGuidance.NO_PERSON)
                is PoseObservation.Frame -> {
                    val updated = requireNotNull(counter).update(observation.frame)
                    mutableProgress.value = mutableProgress.value.copy(
                        repetitions = updated.repetitions,
                        completed = updated.repetitions >= targetCount,
                        guidance = CameraGuidance.NONE,
                        landmarks = observation.frame.landmarks.values.toList(),
                    )
                }
            }
        }
    }

    private fun updateGuidance(guidance: CameraGuidance) {
        mutableProgress.value = mutableProgress.value.copy(guidance = guidance)
    }

    private companion object {
        const val FALLBACK_DELAY_MS = 60_000L
    }
}

private class HandlerChallengeDelayScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : ChallengeDelayScheduler {
    override fun schedule(delayMs: Long, task: () -> Unit): AutoCloseable {
        val runnable = Runnable(task)
        handler.postDelayed(runnable, delayMs)
        return AutoCloseable { handler.removeCallbacks(runnable) }
    }
}
