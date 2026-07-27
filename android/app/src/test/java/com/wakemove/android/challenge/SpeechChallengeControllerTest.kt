package com.wakemove.android.challenge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class SpeechChallengeControllerTest {
    @Test
    fun recognition_starts_only_after_controller_start_with_zh_cn_free_form_partials() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)

        assertEquals(0, source.startCount)

        controller.start("今天也要准时起床")

        assertEquals(1, source.startCount)
        assertEquals(
            SpeechRecognitionRequest(
                languageTag = "zh-CN",
                languageModel = SpeechLanguageModel.FREE_FORM,
                partialResults = true,
            ),
            source.lastRequest,
        )
        assertEquals(
            SpeechChallengeState.Listening(
                phrase = "今天也要准时起床",
                partialText = "",
            ),
            controller.state.value,
        )
    }

    @Test
    fun partial_results_are_displayed_without_deciding_the_challenge() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)
        controller.start("今天也要准时起床")

        source.emit(SpeechRecognitionEvent.Partial(listOf("今天也要", "今天")))

        assertEquals(
            SpeechChallengeState.Listening(
                phrase = "今天也要准时起床",
                partialText = "今天也要",
            ),
            controller.state.value,
        )
    }

    @Test
    fun any_final_candidate_can_match_after_normalization() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)
        controller.start("完成挑战开始新一天2")

        source.emit(
            SpeechRecognitionEvent.Final(
                listOf(
                    "完全不同",
                    "　完成挑战，开始新一天二！",
                    "仍然不同",
                ),
            ),
        )

        assertEquals(
            SpeechChallengeState.Completed(
                phrase = "完成挑战开始新一天2",
                matchedCandidate = "　完成挑战，开始新一天二！",
            ),
            controller.state.value,
        )
    }

    @Test
    fun final_candidates_that_do_not_equal_the_phrase_are_rejected() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)
        controller.start("早安新的开始")

        source.emit(SpeechRecognitionEvent.Final(listOf("早安新的一天", "晚安新的开始")))

        assertEquals(
            SpeechChallengeState.WrongPhrase(
                phrase = "早安新的开始",
                candidates = listOf("早安新的一天", "晚安新的开始"),
            ),
            controller.state.value,
        )
    }

    @Test
    fun empty_final_candidates_are_an_explicit_no_match_state() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)
        controller.start("起床就是胜利")

        source.emit(SpeechRecognitionEvent.Final(emptyList()))

        assertEquals(
            SpeechChallengeState.NoMatch("起床就是胜利"),
            controller.state.value,
        )
    }

    @Test
    fun recognition_errors_map_to_explicit_retryable_states() {
        val expectedStates = mapOf(
            SpeechRecognitionError.NETWORK to SpeechChallengeState.NetworkError("先喝一杯水"),
            SpeechRecognitionError.NO_MATCH to SpeechChallengeState.NoMatch("先喝一杯水"),
            SpeechRecognitionError.PERMISSION_DENIED to
                SpeechChallengeState.PermissionDenied("先喝一杯水"),
            SpeechRecognitionError.SERVICE_UNAVAILABLE to
                SpeechChallengeState.ServiceUnavailable("先喝一杯水"),
        )

        expectedStates.forEach { (error, expected) ->
            val source = FakeSpeechRecognitionSource()
            val controller = SpeechChallengeController(source)
            controller.start("先喝一杯水")

            source.emit(SpeechRecognitionEvent.Error(error))

            assertEquals(expected, controller.state.value)
        }
    }

    @Test
    fun startup_permission_failure_is_contained_as_permission_state() {
        val source = FakeSpeechRecognitionSource().apply {
            startFailure = SecurityException("microphone permission denied")
        }
        val controller = SpeechChallengeController(source)

        controller.start("阳光在等你")

        assertEquals(
            SpeechChallengeState.PermissionDenied("阳光在等你"),
            controller.state.value,
        )
    }

    @Test
    fun unexpected_platform_startup_failure_is_contained_as_service_unavailable() {
        val source = FakeSpeechRecognitionSource().apply {
            startFailure = IllegalStateException("recognition service crashed")
        }
        val controller = SpeechChallengeController(source)

        controller.start("阳光在等你")

        assertEquals(
            SpeechChallengeState.ServiceUnavailable("阳光在等你"),
            controller.state.value,
        )
    }

    @Test
    fun retry_restarts_the_same_phrase_after_wrong_phrase_or_error() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)
        controller.start("把被子叠好")
        source.emit(SpeechRecognitionEvent.Final(listOf("把杯子叠好")))

        controller.retry()
        assertEquals(2, source.startCount)
        assertEquals(
            SpeechChallengeState.Listening("把被子叠好"),
            controller.state.value,
        )

        source.emit(SpeechRecognitionEvent.Error(SpeechRecognitionError.NETWORK))
        controller.retry()
        assertEquals(3, source.startCount)
        assertEquals(
            SpeechChallengeState.Listening("把被子叠好"),
            controller.state.value,
        )
    }

    @Test
    fun stale_callback_from_the_previous_attempt_is_ignored_after_retry() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)
        controller.start("把被子叠好")
        source.emitFromAttempt(
            attemptIndex = 0,
            event = SpeechRecognitionEvent.Final(listOf("把杯子叠好")),
        )

        controller.retry()
        source.emitFromAttempt(
            attemptIndex = 0,
            event = SpeechRecognitionEvent.Final(listOf("把被子叠好")),
        )

        assertEquals(
            SpeechChallengeState.Listening("把被子叠好"),
            controller.state.value,
        )

        source.emitFromAttempt(
            attemptIndex = 1,
            event = SpeechRecognitionEvent.Final(listOf("把被子叠好")),
        )
        assertEquals(
            SpeechChallengeState.Completed("把被子叠好", "把被子叠好"),
            controller.state.value,
        )
    }

    @Test
    fun close_is_idempotent_releases_recognition_and_ignores_late_results() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)
        controller.start("今天会很顺利")

        controller.close()
        controller.close()
        source.emit(SpeechRecognitionEvent.Final(listOf("今天会很顺利")))

        assertEquals(1, source.closeCount)
        assertEquals(SpeechChallengeState.Closed, controller.state.value)
    }

    @Test
    fun close_before_start_releases_recognition_without_starting_it() {
        val source = FakeSpeechRecognitionSource()
        val controller = SpeechChallengeController(source)

        controller.close()

        assertEquals(0, source.startCount)
        assertEquals(1, source.closeCount)
        assertEquals(SpeechChallengeState.Closed, controller.state.value)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidSpeechRecognitionSourceTest {
    @Test
    fun platform_operations_are_marshaled_to_the_main_dispatcher() {
        val dispatcher = QueuedMainThreadDispatcher()
        val platform = FakeAndroidSpeechRecognizerPlatform().apply {
            onPlatformCall = {
                assertTrue("platform call must run on main dispatcher", dispatcher.isRunning)
            }
        }
        val source = AndroidSpeechRecognitionSource(
            RuntimeEnvironment.getApplication(),
            platform,
            dispatcher,
        )

        source.start(SpeechRecognitionRequest.defaultZhCn()) {}

        assertEquals(0, platform.availabilityCheckCount)
        assertEquals(0, platform.createCount)
        dispatcher.runAll()
        assertEquals(1, platform.availabilityCheckCount)
        assertEquals(1, platform.createCount)
        assertEquals(1, platform.session.setListenerCount)
        assertEquals(1, platform.session.startCount)

        source.close()
        assertEquals(0, platform.session.destroyCount)
        dispatcher.runAll()
        assertEquals(1, platform.session.destroyCount)
    }

    @Test
    fun listener_setup_failure_destroys_the_partially_initialized_recognizer() {
        val dispatcher = QueuedMainThreadDispatcher()
        val platform = FakeAndroidSpeechRecognizerPlatform().apply {
            session.setListenerFailure = IllegalStateException("listener setup failed")
        }
        val source = AndroidSpeechRecognitionSource(
            RuntimeEnvironment.getApplication(),
            platform,
            dispatcher,
        )
        val events = mutableListOf<SpeechRecognitionEvent>()

        source.start(SpeechRecognitionRequest.defaultZhCn(), events::add)
        dispatcher.runAll()

        assertEquals(
            listOf(
                SpeechRecognitionEvent.Error(SpeechRecognitionError.SERVICE_UNAVAILABLE),
            ),
            events,
        )
        assertEquals(1, platform.session.destroyCount)
    }

    @Test
    fun platform_recognizer_is_created_lazily_and_receives_the_required_intent() {
        val context = RuntimeEnvironment.getApplication()
        val platform = FakeAndroidSpeechRecognizerPlatform()
        val source = AndroidSpeechRecognitionSource(context, platform)

        assertEquals(0, platform.createCount)

        source.start(SpeechRecognitionRequest.defaultZhCn()) {}

        assertEquals(1, platform.createCount)
        val intent = platform.session.lastIntent!!
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals(
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL),
        )
        assertEquals("zh-CN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
    }

    @Test
    fun platform_callbacks_preserve_partial_and_final_candidate_lists() {
        val source = AndroidSpeechRecognitionSource(
            RuntimeEnvironment.getApplication(),
            FakeAndroidSpeechRecognizerPlatform().also { platform ->
                platform.session.onStart = {
                    platform.session.listener?.onPartialResults(
                        recognitionResults("部分", "候选"),
                    )
                    platform.session.listener?.onResults(
                        recognitionResults("第一候选", "第二候选"),
                    )
                }
            },
        )
        val events = mutableListOf<SpeechRecognitionEvent>()

        source.start(SpeechRecognitionRequest.defaultZhCn(), events::add)

        assertEquals(
            listOf(
                SpeechRecognitionEvent.Partial(listOf("部分", "候选")),
                SpeechRecognitionEvent.Final(listOf("第一候选", "第二候选")),
            ),
            events,
        )
    }

    @Test
    fun platform_callbacks_remain_bound_to_the_attempt_that_registered_them() {
        val platform = FakeAndroidSpeechRecognizerPlatform()
        val source = AndroidSpeechRecognitionSource(
            RuntimeEnvironment.getApplication(),
            platform,
        )
        val firstAttemptEvents = mutableListOf<SpeechRecognitionEvent>()
        val secondAttemptEvents = mutableListOf<SpeechRecognitionEvent>()

        source.start(SpeechRecognitionRequest.defaultZhCn(), firstAttemptEvents::add)
        val firstAttemptListener = platform.session.listenerHistory.single()
        firstAttemptListener.onError(SpeechRecognizer.ERROR_NO_MATCH)

        source.start(SpeechRecognitionRequest.defaultZhCn(), secondAttemptEvents::add)
        val secondAttemptListener = platform.session.listenerHistory.last()
        firstAttemptListener.onResults(recognitionResults("迟到的旧结果"))

        assertEquals(
            listOf(
                SpeechRecognitionEvent.Error(SpeechRecognitionError.NO_MATCH),
                SpeechRecognitionEvent.Final(listOf("迟到的旧结果")),
            ),
            firstAttemptEvents,
        )
        assertTrue(secondAttemptEvents.isEmpty())

        secondAttemptListener.onResults(recognitionResults("当前结果"))
        assertEquals(
            listOf(SpeechRecognitionEvent.Final(listOf("当前结果"))),
            secondAttemptEvents,
        )
    }

    @Test
    fun android_errors_map_to_network_no_match_permission_and_service_unavailable() {
        val cases = mapOf(
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT to SpeechRecognitionError.NETWORK,
            SpeechRecognizer.ERROR_NETWORK to SpeechRecognitionError.NETWORK,
            SpeechRecognizer.ERROR_SERVER to SpeechRecognitionError.NETWORK,
            SpeechRecognizer.ERROR_NO_MATCH to SpeechRecognitionError.NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to SpeechRecognitionError.NO_MATCH,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to
                SpeechRecognitionError.PERMISSION_DENIED,
            SpeechRecognizer.ERROR_AUDIO to SpeechRecognitionError.SERVICE_UNAVAILABLE,
            SpeechRecognizer.ERROR_CLIENT to SpeechRecognitionError.SERVICE_UNAVAILABLE,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY to
                SpeechRecognitionError.SERVICE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED to
                SpeechRecognitionError.SERVICE_UNAVAILABLE,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED to
                SpeechRecognitionError.SERVICE_UNAVAILABLE,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE to
                SpeechRecognitionError.SERVICE_UNAVAILABLE,
        )

        cases.forEach { (platformError, expected) ->
            assertEquals(expected, mapSpeechRecognizerError(platformError))
        }
    }

    @Test
    fun unavailable_service_reports_explicit_error_without_creating_a_recognizer() {
        val platform = FakeAndroidSpeechRecognizerPlatform().apply {
            recognitionAvailable = false
        }
        val source = AndroidSpeechRecognitionSource(
            RuntimeEnvironment.getApplication(),
            platform,
        )
        val events = mutableListOf<SpeechRecognitionEvent>()

        source.start(SpeechRecognitionRequest.defaultZhCn(), events::add)

        assertEquals(
            listOf(
                SpeechRecognitionEvent.Error(SpeechRecognitionError.SERVICE_UNAVAILABLE),
            ),
            events,
        )
        assertEquals(0, platform.createCount)
    }

    @Test
    fun close_destroys_the_platform_recognizer_once() {
        val platform = FakeAndroidSpeechRecognizerPlatform()
        val source = AndroidSpeechRecognitionSource(
            RuntimeEnvironment.getApplication(),
            platform,
        )
        source.start(SpeechRecognitionRequest.defaultZhCn()) {}

        source.close()
        source.close()

        assertEquals(1, platform.session.destroyCount)
    }

    private fun recognitionResults(vararg candidates: String) = Bundle().apply {
        putStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION,
            arrayListOf(*candidates),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SpeechPhraseProviderTest {
    @Test
    fun loads_the_canonical_shared_phrase_asset() {
        val provider = PhraseProvider(
            RuntimeEnvironment.getApplication().assets,
            ZeroSecureRandom(),
        )

        assertEquals("今天也要准时起床", provider.nextPhrase())
    }
}

private class FakeSpeechRecognitionSource : SpeechRecognitionSource {
    var startCount = 0
        private set
    var closeCount = 0
        private set
    var lastRequest: SpeechRecognitionRequest? = null
        private set
    var startFailure: Throwable? = null
    private val listeners = mutableListOf<(SpeechRecognitionEvent) -> Unit>()

    override fun start(
        request: SpeechRecognitionRequest,
        listener: (SpeechRecognitionEvent) -> Unit,
    ) {
        startCount += 1
        lastRequest = request
        startFailure?.let { throw it }
        listeners += listener
    }

    fun emit(event: SpeechRecognitionEvent) {
        listeners.lastOrNull()?.invoke(event)
    }

    fun emitFromAttempt(
        attemptIndex: Int,
        event: SpeechRecognitionEvent,
    ) {
        listeners[attemptIndex].invoke(event)
    }

    override fun close() {
        closeCount += 1
    }
}

private class FakeAndroidSpeechRecognizerPlatform : AndroidSpeechRecognizerPlatform {
    var recognitionAvailable = true
    var availabilityCheckCount = 0
        private set
    var createCount = 0
        private set
    val session = FakeAndroidSpeechRecognizerSession()
    var onPlatformCall: () -> Unit = {}
        set(value) {
            field = value
            session.onPlatformCall = value
        }

    override fun isRecognitionAvailable(context: Context): Boolean {
        onPlatformCall()
        availabilityCheckCount += 1
        return recognitionAvailable
    }

    override fun create(context: Context): AndroidSpeechRecognizerSession {
        onPlatformCall()
        createCount += 1
        return session
    }
}

private class FakeAndroidSpeechRecognizerSession : AndroidSpeechRecognizerSession {
    var listener: RecognitionListener? = null
        private set
    val listenerHistory = mutableListOf<RecognitionListener>()
    var setListenerCount = 0
        private set
    var lastIntent: Intent? = null
        private set
    var startCount = 0
        private set
    var destroyCount = 0
        private set
    var onStart: () -> Unit = {}
    var onPlatformCall: () -> Unit = {}
    var setListenerFailure: Throwable? = null

    override fun setRecognitionListener(listener: RecognitionListener) {
        onPlatformCall()
        setListenerCount += 1
        setListenerFailure?.let { throw it }
        this.listener = listener
        listenerHistory += listener
    }

    override fun startListening(intent: Intent) {
        onPlatformCall()
        startCount += 1
        lastIntent = intent
        onStart()
    }

    override fun destroy() {
        onPlatformCall()
        destroyCount += 1
    }
}

private class QueuedMainThreadDispatcher : SpeechMainThreadDispatcher {
    private val tasks = ArrayDeque<() -> Unit>()
    var isRunning = false
        private set

    override fun dispatch(task: () -> Unit) {
        tasks.addLast(task)
    }

    fun runAll() {
        while (tasks.isNotEmpty()) {
            isRunning = true
            try {
                tasks.removeFirst().invoke()
            } finally {
                isRunning = false
            }
        }
    }
}

private class ZeroSecureRandom : SecureRandom() {
    override fun nextInt(bound: Int): Int = 0
}
