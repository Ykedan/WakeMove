package com.wakemove.android.challenge

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.wakemove.android.domain.ChallengeType
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PoseLandmarkerAdapterTest {
    @Test
    fun `owned frame gate admits one frame and closes dropped or completed resources`() {
        val gate = OwnedFrameGate()
        val first = CloseTracker()
        val dropped = CloseTracker()

        assertTrue(gate.tryAcquire(first))
        assertFalse(gate.tryAcquire(dropped))
        assertTrue(dropped.closed)
        assertFalse(first.closed)

        gate.release()
        assertTrue(first.closed)
        assertTrue(gate.tryAcquire(CloseTracker()))
        gate.close()
        assertFalse(gate.hasInFlight)
    }

    @Test
    fun synchronous_camera_provider_failure_preserves_guidance_and_timed_fallback() {
        val context = RuntimeEnvironment.getApplication()
        val platform = ThrowingCameraProviderPlatform()
        val scheduler = AdvancingDelayScheduler()
        val adapter = PoseLandmarkerAdapter(
            context = context,
            lifecycleOwner = ResumedLifecycleOwner(),
            previewView = PreviewView(context),
            platform = platform,
        )
        val controller = CameraChallengeController(adapter, scheduler)

        controller.start(ChallengeType.SQUAT, targetCount = 1)
        assertEquals(CameraGuidance.NO_PERSON, controller.progress.value.guidance)

        scheduler.advanceBy(60_000L)
        assertTrue(controller.progress.value.fallbackAvailable)

        controller.close()
        assertTrue(platform.executor.isShutdown)
    }

    @Test
    fun executor_submission_failure_preserves_guidance_and_timed_fallback() {
        val context = RuntimeEnvironment.getApplication()
        val platform = RejectingExecutorPlatform()
        val scheduler = AdvancingDelayScheduler()
        val adapter = PoseLandmarkerAdapter(
            context = context,
            lifecycleOwner = ResumedLifecycleOwner(),
            previewView = PreviewView(context),
            platform = platform,
        )
        val controller = CameraChallengeController(adapter, scheduler)

        controller.start(ChallengeType.HANDS_UP, targetCount = 1)
        assertEquals(CameraGuidance.NO_PERSON, controller.progress.value.guidance)

        scheduler.advanceBy(60_000L)
        assertTrue(controller.progress.value.fallbackAvailable)

        controller.close()
        assertTrue(platform.executor.isShutdown)
    }
}

private class CloseTracker : AutoCloseable {
    var closed = false
    override fun close() {
        closed = true
    }
}

private class ThrowingCameraProviderPlatform : PoseLandmarkerPlatform {
    val executor = HoldingExecutorService()

    override fun newAnalysisExecutor(): ExecutorService = executor

    override fun cameraProviderFuture(
        context: Context,
    ): ListenableFuture<ProcessCameraProvider> = error("CameraX startup failed")
}

private class HoldingExecutorService : AbstractExecutorService() {
    private var shutdown = false
    private val tasks = mutableListOf<Runnable>()

    override fun execute(command: Runnable) {
        check(!shutdown)
        tasks += command
    }

    override fun shutdown() {
        shutdown = true
        tasks.clear()
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown = true
        val pending = tasks.toList()
        tasks.clear()
        return pending
    }

    override fun isShutdown(): Boolean = shutdown

    override fun isTerminated(): Boolean = shutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
}

private class RejectingExecutorPlatform : PoseLandmarkerPlatform {
    val executor = RejectingExecutorService()

    override fun newAnalysisExecutor(): ExecutorService = executor

    override fun cameraProviderFuture(
        context: Context,
    ): ListenableFuture<ProcessCameraProvider> = error("must not request CameraX after rejected submission")
}

private class RejectingExecutorService : AbstractExecutorService() {
    private var shutdown = false

    override fun execute(command: Runnable) {
        throw RejectedExecutionException("executor rejected startup")
    }

    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown = true
        return emptyList()
    }

    override fun isShutdown(): Boolean = shutdown

    override fun isTerminated(): Boolean = shutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
}

private class ResumedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle = registry
}

private class AdvancingDelayScheduler : ChallengeDelayScheduler {
    private var nowMs = 0L
    private var scheduledAtMs = Long.MAX_VALUE
    private var cancelled = false
    private var task: (() -> Unit)? = null

    override fun schedule(delayMs: Long, task: () -> Unit): AutoCloseable {
        scheduledAtMs = nowMs + delayMs
        this.task = task
        return AutoCloseable { cancelled = true }
    }

    fun advanceBy(durationMs: Long) {
        nowMs += durationMs
        if (!cancelled && nowMs >= scheduledAtMs) task?.invoke()
    }
}
