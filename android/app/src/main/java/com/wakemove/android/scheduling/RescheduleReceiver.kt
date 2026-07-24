package com.wakemove.android.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

interface SchedulingDependencies {
    val alarmScheduler: AlarmScheduler
    val pendingScheduleRecovery: PendingScheduleRecovery
}

class RescheduleReceiver() : BroadcastReceiver() {
    private var schedulerProvider: (Context) -> AlarmScheduler = { context ->
        val dependencies = context.applicationContext as? SchedulingDependencies
            ?: error("Application must implement SchedulingDependencies")
        dependencies.alarmScheduler
    }
    private var executor: Executor = RECOVERY_EXECUTOR
    private var pendingRecovery: suspend (Context) -> Unit = { context ->
        val dependencies = context.applicationContext as? SchedulingDependencies
            ?: error("Application must implement SchedulingDependencies")
        dependencies.pendingScheduleRecovery.recover()
    }

    internal constructor(
        schedulerProvider: (Context) -> AlarmScheduler,
        executor: Executor,
        pendingRecovery: suspend (Context) -> Unit = {},
    ) : this() {
        this.schedulerProvider = schedulerProvider
        this.executor = executor
        this.pendingRecovery = pendingRecovery
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        try {
            executor.execute {
                try {
                    runBlocking {
                        try {
                            schedulerProvider(context).rescheduleAll()
                        } catch (error: Exception) {
                            Log.e(TAG, "Unable to restore regular alarm schedule", error)
                        }
                        try {
                            pendingRecovery(context)
                        } catch (error: Exception) {
                            Log.e(TAG, "Unable to restore pending alarm schedule", error)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (error: RuntimeException) {
            pendingResult.finish()
            Log.e(TAG, "Unable to enqueue alarm schedule recovery", error)
        }
    }

    companion object {
        private const val TAG = "RescheduleReceiver"

        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        private val RECOVERY_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wakemove-schedule-recovery").apply {
                isDaemon = true
            }
        }
    }
}
