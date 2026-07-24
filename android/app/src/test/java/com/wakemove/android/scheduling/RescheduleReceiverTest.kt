package com.wakemove.android.scheduling

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import com.wakemove.android.domain.Alarm
import java.time.Instant
import java.util.concurrent.Executor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class RescheduleReceiverTest {
    @Test
    fun `recovery runs asynchronously and finishes the pending broadcast`() {
        val scheduler = RecordingScheduler()
        val executor = QueuedExecutor()
        val receiver = RescheduleReceiver(
            schedulerProvider = { scheduler },
            executor = executor,
        )
        val receiverShadow = shadowOf(receiver)

        val context: Context = RuntimeEnvironment.getApplication()
        context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BOOT_COMPLETED),
            Context.RECEIVER_EXPORTED,
        )
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()

        val pendingResult = checkNotNull(receiverShadow.originalPendingResult)
        assertTrue(receiverShadow.wentAsync())
        assertFalse(scheduler.rescheduled)
        assertFalse(shadowOf(pendingResult).future.isDone)

        executor.runNext()

        assertTrue(scheduler.rescheduled)
        assertTrue(shadowOf(pendingResult).future.isDone)
    }

    @Test
    fun `recovery finishes the pending broadcast when rescheduling fails`() {
        val executor = QueuedExecutor()
        val receiver = RescheduleReceiver(
            schedulerProvider = { RecordingScheduler(fail = true) },
            executor = executor,
        )
        val receiverShadow = shadowOf(receiver)

        val context: Context = RuntimeEnvironment.getApplication()
        context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_TIME_CHANGED),
            Context.RECEIVER_EXPORTED,
        )
        context.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        val pendingResult = checkNotNull(receiverShadow.originalPendingResult)

        executor.runNext()

        assertTrue(shadowOf(pendingResult).future.isDone)
    }
}

private class QueuedExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runNext() {
        tasks.removeFirst().run()
    }
}

private class RecordingScheduler(
    private val fail: Boolean = false,
) : AlarmScheduler {
    var rescheduled = false

    override fun schedule(alarm: Alarm, at: Instant) = Unit

    override fun cancel(alarmId: String) = Unit

    override suspend fun rescheduleAll() {
        if (fail) error("boom")
        rescheduled = true
    }
}
