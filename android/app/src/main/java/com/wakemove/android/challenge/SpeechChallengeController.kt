package com.wakemove.android.challenge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeechLanguageModel {
    FREE_FORM,
}

data class SpeechRecognitionRequest(
    val languageTag: String,
    val languageModel: SpeechLanguageModel,
    val partialResults: Boolean,
) {
    companion object {
        fun defaultZhCn() = SpeechRecognitionRequest(
            languageTag = "zh-CN",
            languageModel = SpeechLanguageModel.FREE_FORM,
            partialResults = true,
        )
    }
}

enum class SpeechRecognitionError {
    NETWORK,
    NO_MATCH,
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
}

sealed interface SpeechRecognitionEvent {
    data object Preparing : SpeechRecognitionEvent

    data class Partial(val candidates: List<String>) : SpeechRecognitionEvent

    data class Final(val candidates: List<String>) : SpeechRecognitionEvent

    data class Error(val error: SpeechRecognitionError) : SpeechRecognitionEvent
}

fun interface SpeechRecognitionSource : AutoCloseable {
    fun start(
        request: SpeechRecognitionRequest,
        listener: (SpeechRecognitionEvent) -> Unit,
    )

    override fun close() = Unit
}

sealed interface SpeechChallengeState {
    data object Idle : SpeechChallengeState

    data class Preparing(val phrase: String) : SpeechChallengeState

    data class Listening(
        val phrase: String,
        val partialText: String = "",
    ) : SpeechChallengeState

    data class Completed(
        val phrase: String,
        val matchedCandidate: String,
    ) : SpeechChallengeState

    data class WrongPhrase(
        val phrase: String,
        val candidates: List<String>,
    ) : SpeechChallengeState

    data class NetworkError(val phrase: String) : SpeechChallengeState

    data class NoMatch(val phrase: String) : SpeechChallengeState

    data class PermissionDenied(val phrase: String) : SpeechChallengeState

    data class ServiceUnavailable(val phrase: String) : SpeechChallengeState

    data object Closed : SpeechChallengeState
}

class SpeechChallengeController(
    private val recognitionSource: SpeechRecognitionSource,
) : AutoCloseable {
    constructor(context: Context) : this(OfflineChineseSpeechRecognitionSource(context))

    private val lock = Any()
    private val mutableState = MutableStateFlow<SpeechChallengeState>(SpeechChallengeState.Idle)
    private var phrase: String? = null
    private var started = false
    private var closed = false
    private var attemptGeneration = 0L

    val state: StateFlow<SpeechChallengeState> = mutableState.asStateFlow()

    fun start(phrase: String) {
        require(phrase.isNotBlank()) { "phrase must not be blank" }
        synchronized(lock) {
            check(!closed) { "SpeechChallengeController is closed" }
            check(!started) { "SpeechChallengeController is already started" }
            started = true
            this.phrase = phrase
        }
        beginListening(phrase)
    }

    fun retry() {
        val currentPhrase = synchronized(lock) {
            check(!closed) { "SpeechChallengeController is closed" }
            check(started) { "SpeechChallengeController has not started" }
            check(mutableState.value.isRetryable()) {
                "SpeechChallengeController is not ready to retry"
            }
            requireNotNull(phrase)
        }
        beginListening(currentPhrase)
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            attemptGeneration += 1
            mutableState.value = SpeechChallengeState.Closed
        }
        recognitionSource.close()
    }

    private fun beginListening(currentPhrase: String) {
        val generation = synchronized(lock) {
            if (closed) return
            attemptGeneration += 1
            mutableState.value = SpeechChallengeState.Listening(currentPhrase)
            attemptGeneration
        }
        try {
            recognitionSource.start(
                SpeechRecognitionRequest.defaultZhCn(),
            ) { event ->
                onRecognitionEvent(generation, event)
            }
        } catch (_: SecurityException) {
            onRecognitionEvent(
                generation,
                SpeechRecognitionEvent.Error(SpeechRecognitionError.PERMISSION_DENIED),
            )
        } catch (_: Throwable) {
            onRecognitionEvent(
                generation,
                SpeechRecognitionEvent.Error(SpeechRecognitionError.SERVICE_UNAVAILABLE),
            )
        }
    }

    private fun onRecognitionEvent(
        generation: Long,
        event: SpeechRecognitionEvent,
    ) {
        synchronized(lock) {
            if (closed ||
                generation != attemptGeneration ||
                (mutableState.value !is SpeechChallengeState.Listening &&
                    mutableState.value !is SpeechChallengeState.Preparing)
            ) {
                return
            }
            val currentPhrase = requireNotNull(phrase)
            mutableState.value = when (event) {
                SpeechRecognitionEvent.Preparing ->
                    SpeechChallengeState.Preparing(currentPhrase)

                is SpeechRecognitionEvent.Partial -> SpeechChallengeState.Listening(
                    phrase = currentPhrase,
                    partialText = event.candidates.firstOrNull().orEmpty(),
                )

                is SpeechRecognitionEvent.Final -> matchFinalCandidates(
                    currentPhrase,
                    event.candidates,
                )

                is SpeechRecognitionEvent.Error -> event.error.toState(currentPhrase)
            }
        }
    }
}

private fun SpeechChallengeState.isRetryable(): Boolean = when (this) {
    is SpeechChallengeState.WrongPhrase,
    is SpeechChallengeState.NetworkError,
    is SpeechChallengeState.NoMatch,
    is SpeechChallengeState.PermissionDenied,
    is SpeechChallengeState.ServiceUnavailable,
    -> true

    else -> false
}

private fun matchFinalCandidates(
    phrase: String,
    candidates: List<String>,
): SpeechChallengeState {
    if (candidates.isEmpty()) return SpeechChallengeState.NoMatch(phrase)
    val normalizedPhrase = SpeechNormalizer.normalize(phrase)
    val matched = candidates.firstOrNull {
        SpeechNormalizer.normalize(it) == normalizedPhrase
    }
    return if (matched != null) {
        SpeechChallengeState.Completed(phrase, matched)
    } else {
        SpeechChallengeState.WrongPhrase(phrase, candidates.toList())
    }
}

private fun SpeechRecognitionError.toState(phrase: String): SpeechChallengeState = when (this) {
    SpeechRecognitionError.NETWORK -> SpeechChallengeState.NetworkError(phrase)
    SpeechRecognitionError.NO_MATCH -> SpeechChallengeState.NoMatch(phrase)
    SpeechRecognitionError.PERMISSION_DENIED -> SpeechChallengeState.PermissionDenied(phrase)
    SpeechRecognitionError.SERVICE_UNAVAILABLE ->
        SpeechChallengeState.ServiceUnavailable(phrase)
}

internal interface AndroidSpeechRecognizerPlatform {
    fun isRecognitionAvailable(context: Context): Boolean

    fun create(context: Context): AndroidSpeechRecognizerSession
}

internal interface AndroidSpeechRecognizerSession {
    fun setRecognitionListener(listener: RecognitionListener)

    fun startListening(intent: Intent)

    fun destroy()
}

internal fun interface SpeechMainThreadDispatcher {
    fun dispatch(task: () -> Unit)
}

internal class AndroidSpeechRecognitionSource(
    context: Context,
    private val platform: AndroidSpeechRecognizerPlatform = SystemAndroidSpeechRecognizerPlatform,
    private val mainThreadDispatcher: SpeechMainThreadDispatcher =
        HandlerSpeechMainThreadDispatcher(),
) : SpeechRecognitionSource {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var session: AndroidSpeechRecognizerSession? = null
    private var closed = false

    override fun start(
        request: SpeechRecognitionRequest,
        listener: (SpeechRecognitionEvent) -> Unit,
    ) {
        synchronized(lock) {
            if (closed) return
        }

        mainThreadDispatcher.dispatch {
            startOnMainThread(request, listener)
        }
    }

    private fun startOnMainThread(
        request: SpeechRecognitionRequest,
        listener: (SpeechRecognitionEvent) -> Unit,
    ) {
        synchronized(lock) {
            if (closed) return
        }
        try {
            if (!platform.isRecognitionAvailable(applicationContext)) {
                report(listener, SpeechRecognitionError.SERVICE_UNAVAILABLE)
                return
            }
            val recognizer = prepareRecognizer(listener) ?: return
            synchronized(lock) {
                if (closed) return
            }
            recognizer.startListening(request.toRecognizerIntent())
        } catch (_: SecurityException) {
            report(listener, SpeechRecognitionError.PERMISSION_DENIED)
        } catch (_: Throwable) {
            report(listener, SpeechRecognitionError.SERVICE_UNAVAILABLE)
        }
    }

    private fun prepareRecognizer(
        listener: (SpeechRecognitionEvent) -> Unit,
    ): AndroidSpeechRecognizerSession? {
        val existing = synchronized(lock) {
            if (closed) return null
            session
        }
        val platformListener = PlatformRecognitionListener { event ->
            report(listener, event)
        }
        if (existing != null) {
            try {
                existing.setRecognitionListener(platformListener)
                return existing
            } catch (error: Throwable) {
                synchronized(lock) {
                    if (session === existing) session = null
                }
                runCatching { existing.destroy() }
                throw error
            }
        }

        val created = platform.create(applicationContext)
        try {
            created.setRecognitionListener(platformListener)
        } catch (error: Throwable) {
            runCatching { created.destroy() }
            throw error
        }
        val published = synchronized(lock) {
            if (closed) {
                false
            } else {
                session = created
                true
            }
        }
        if (!published) {
            runCatching { created.destroy() }
            return null
        }
        return created
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
        }
        mainThreadDispatcher.dispatch {
            val recognizer = synchronized(lock) {
                session.also { session = null }
            }
            runCatching { recognizer?.destroy() }
        }
    }

    private fun report(
        listener: (SpeechRecognitionEvent) -> Unit,
        event: SpeechRecognitionEvent,
    ) {
        val canReport = synchronized(lock) { !closed }
        if (canReport) listener(event)
    }

    private fun report(
        listener: (SpeechRecognitionEvent) -> Unit,
        error: SpeechRecognitionError,
    ) {
        report(listener, SpeechRecognitionEvent.Error(error))
    }
}

private class HandlerSpeechMainThreadDispatcher(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : SpeechMainThreadDispatcher {
    override fun dispatch(task: () -> Unit) {
        if (Looper.myLooper() == handler.looper) {
            task()
        } else {
            handler.post(task)
        }
    }
}

private class PlatformRecognitionListener(
    private val report: (SpeechRecognitionEvent) -> Unit,
) : RecognitionListener {
    override fun onPartialResults(partialResults: Bundle) {
        report(SpeechRecognitionEvent.Partial(partialResults.recognitionCandidates()))
    }

    override fun onResults(results: Bundle) {
        report(SpeechRecognitionEvent.Final(results.recognitionCandidates()))
    }

    override fun onError(error: Int) {
        report(SpeechRecognitionEvent.Error(mapSpeechRecognizerError(error)))
    }

    override fun onReadyForSpeech(params: Bundle) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onEvent(eventType: Int, params: Bundle) = Unit
}

private fun Bundle.recognitionCandidates(): List<String> =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.toList().orEmpty()

private fun SpeechRecognitionRequest.toRecognizerIntent() =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            when (languageModel) {
                SpeechLanguageModel.FREE_FORM -> RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            },
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
    }

internal fun mapSpeechRecognizerError(error: Int): SpeechRecognitionError = when (error) {
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_SERVER,
    -> SpeechRecognitionError.NETWORK

    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> SpeechRecognitionError.NO_MATCH

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        SpeechRecognitionError.PERMISSION_DENIED

    else -> SpeechRecognitionError.SERVICE_UNAVAILABLE
}

private object SystemAndroidSpeechRecognizerPlatform : AndroidSpeechRecognizerPlatform {
    override fun isRecognitionAvailable(context: Context): Boolean =
        systemSpeechRecognitionAvailable(context)

    override fun create(context: Context): AndroidSpeechRecognizerSession =
        SystemAndroidSpeechRecognizerSession(
            if (onDeviceSpeechRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            },
        )
}

internal fun systemSpeechRecognitionAvailable(context: Context): Boolean =
    onDeviceSpeechRecognitionAvailable(context) ||
        SpeechRecognizer.isRecognitionAvailable(context)

private fun onDeviceSpeechRecognitionAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

private class SystemAndroidSpeechRecognizerSession(
    private val recognizer: SpeechRecognizer,
) : AndroidSpeechRecognizerSession {
    override fun setRecognitionListener(listener: RecognitionListener) {
        recognizer.setRecognitionListener(listener)
    }

    override fun startListening(intent: Intent) {
        recognizer.startListening(intent)
    }

    override fun destroy() {
        recognizer.destroy()
    }
}
