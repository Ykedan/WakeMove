package com.wakemove.android.challenge

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.android.StorageService

@RunWith(AndroidJUnit4::class)
class OfflineChineseModelTest {
    @Test(timeout = 120_000)
    fun bundledChineseModelCanBeUnpackedAndLoaded() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        StorageService.unpack(
            context,
            "vosk-model-small-cn-0.22",
            "vosk-model-cn-instrumentation",
            { model ->
                runCatching { model.close() }
                completed.countDown()
            },
            { error ->
                failure.set(error)
                completed.countDown()
            },
        )

        assertTrue(
            "Bundled Chinese model did not finish loading",
            completed.await(110, TimeUnit.SECONDS),
        )
        assertNull("Bundled Chinese model failed to load", failure.get())
    }
}
