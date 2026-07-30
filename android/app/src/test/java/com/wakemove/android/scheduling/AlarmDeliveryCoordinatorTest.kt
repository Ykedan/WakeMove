package com.wakemove.android.scheduling

import android.content.Context
import com.wakemove.android.domain.Alarm
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AlarmDeliveryCoordinatorTest {
    private lateinit var context: Context
    private val firedAt = Instant.parse("2026-07-30T00:30:00Z")
    private val nextAt = Instant.parse("2026-07-31T00:30:00Z")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("wakemove_alarm_delivery", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `next repeat is registered before foreground service is requested`() = runBlocking {
        val order = mutableListOf<String>()
        val diagnostics = diagnostics()
        val coordinator = AlarmDeliveryCoordinator(
            context = context,
            scheduler = RecordingDeliveryScheduler(order, nextAt),
            diagnostics = diagnostics,
            serviceStarter = { _, _ -> order += "service" },
        )

        coordinator.deliver("alarm", firedAt)

        assertEquals(listOf("delivered", "repeat", "service"), order)
        assertEquals(DeliveryStage.SERVICE_START_REQUESTED, diagnostics.latest()?.stage)
        assertEquals(nextAt, diagnostics.latest()?.nextRepeatAt)
    }

    @Test
    fun `service failure keeps next repeat and publishes fallback`() = runBlocking {
        val order = mutableListOf<String>()
        val fallbackIds = mutableListOf<String>()
        val diagnostics = diagnostics()
        val coordinator = AlarmDeliveryCoordinator(
            context = context,
            scheduler = RecordingDeliveryScheduler(order, nextAt),
            diagnostics = diagnostics,
            fallbackPublisher = { fallbackIds += it },
            serviceStarter = { _, _ ->
                order += "service"
                throw IllegalStateException("blocked")
            },
        )

        coordinator.deliver("alarm", firedAt)

        assertEquals(listOf("delivered", "repeat", "service"), order)
        assertEquals(listOf("alarm"), fallbackIds)
        assertEquals(DeliveryStage.FAILED, diagnostics.latest()?.stage)
        assertEquals(DeliveryStage.SERVICE_START_REQUESTED, diagnostics.latest()?.failureStage)
        assertEquals(nextAt, diagnostics.latest()?.nextRepeatAt)
    }

    private fun diagnostics() = AlarmDeliveryDiagnostics(
        context,
        Clock.fixed(firedAt.plusSeconds(1), ZoneOffset.UTC),
    )
}

private class RecordingDeliveryScheduler(
    private val order: MutableList<String>,
    private val nextAt: Instant,
) : AlarmScheduler {
    override fun schedule(alarm: Alarm, at: Instant) = Unit
    override fun cancel(alarmId: String) = Unit
    override suspend fun rescheduleAll() = Unit
    override fun onAlarmDelivered(alarmId: String) {
        order += "delivered"
    }
    override suspend fun registerNextRepeatAfterDelivery(
        alarmId: String,
        deliveredAt: Instant,
    ): Instant {
        order += "repeat"
        return nextAt
    }
}
