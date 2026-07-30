package com.wakemove.android.challenge

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * App-contained, offline Chinese speech recognition.
 *
 * The model is unpacked from APK assets on first use. Nothing is downloaded and
 * no Android RecognitionService implementation is required on the device.
 */
internal class OfflineChineseSpeechRecognitionSource(
    context: Context,
) : SpeechRecognitionSource {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var model: Model? = null
    private var modelLoading = false
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var closed = false
    private var generation = 0L

    override fun start(
        request: SpeechRecognitionRequest,
        listener: (SpeechRecognitionEvent) -> Unit,
    ) {
        val (attempt, previousListening) = synchronized(lock) {
            if (closed) return
            generation += 1
            generation to detachListeningLocked()
        }
        stopListening(previousListening)
        listener(SpeechRecognitionEvent.Preparing)

        val readyModel = synchronized(lock) { model }
        if (readyModel != null) {
            startListening(attempt, readyModel, listener)
            return
        }

        val shouldLoad = synchronized(lock) {
            if (closed || attempt != generation) return
            if (modelLoading) {
                false
            } else {
                modelLoading = true
                true
            }
        }
        if (!shouldLoad) return

        StorageService.unpack(
            applicationContext,
            MODEL_ASSET_PATH,
            MODEL_STORAGE_PATH,
            { unpackedModel ->
                val keepModel = synchronized(lock) {
                    modelLoading = false
                    if (closed) {
                        false
                    } else {
                        model = unpackedModel
                        true
                    }
                }
                if (!keepModel) {
                    runCatching { unpackedModel.close() }
                    return@unpack
                }
                startListening(attempt, unpackedModel, listener)
            },
            {
                synchronized(lock) { modelLoading = false }
                report(
                    attempt,
                    listener,
                    SpeechRecognitionEvent.Error(
                        SpeechRecognitionError.SERVICE_UNAVAILABLE,
                    ),
                )
            },
        )
    }

    override fun close() {
        val (loadedModel, previousListening) = synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            model.also { model = null } to detachListeningLocked()
        }
        stopListening(previousListening)
        runCatching { loadedModel?.close() }
    }

    private fun startListening(
        attempt: Long,
        loadedModel: Model,
        listener: (SpeechRecognitionEvent) -> Unit,
    ) {
        val newRecognizer: Recognizer
        val newService: SpeechService
        try {
            newRecognizer = Recognizer(loadedModel, SAMPLE_RATE)
            newService = SpeechService(newRecognizer, SAMPLE_RATE)
        } catch (_: SecurityException) {
            report(
                attempt,
                listener,
                SpeechRecognitionEvent.Error(SpeechRecognitionError.PERMISSION_DENIED),
            )
            return
        } catch (_: Throwable) {
            report(
                attempt,
                listener,
                SpeechRecognitionEvent.Error(SpeechRecognitionError.SERVICE_UNAVAILABLE),
            )
            return
        }

        val published = synchronized(lock) {
            if (closed || attempt != generation) {
                false
            } else {
                recognizer = newRecognizer
                speechService = newService
                true
            }
        }
        if (!published) {
            runCatching { newService.shutdown() }
            runCatching { newRecognizer.close() }
            return
        }

        try {
            newService.startListening(
                VoskListener(attempt, listener),
                LISTENING_TIMEOUT_MILLIS,
            )
            report(attempt, listener, SpeechRecognitionEvent.Partial(emptyList()))
        } catch (_: SecurityException) {
            report(
                attempt,
                listener,
                SpeechRecognitionEvent.Error(SpeechRecognitionError.PERMISSION_DENIED),
            )
        } catch (_: Throwable) {
            report(
                attempt,
                listener,
                SpeechRecognitionEvent.Error(SpeechRecognitionError.SERVICE_UNAVAILABLE),
            )
        }
    }

    private inner class VoskListener(
        private val attempt: Long,
        private val listener: (SpeechRecognitionEvent) -> Unit,
    ) : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            val text = parseVoskText(hypothesis, "partial")
            report(attempt, listener, SpeechRecognitionEvent.Partial(listOf(text)))
        }

        override fun onResult(hypothesis: String) {
            val text = parseVoskText(hypothesis, "text")
            if (text.isNotBlank()) {
                report(attempt, listener, SpeechRecognitionEvent.Final(listOf(text)))
            }
        }

        override fun onFinalResult(hypothesis: String) {
            val text = parseVoskText(hypothesis, "text")
            report(
                attempt,
                listener,
                if (text.isBlank()) {
                    SpeechRecognitionEvent.Error(SpeechRecognitionError.NO_MATCH)
                } else {
                    SpeechRecognitionEvent.Final(listOf(text))
                },
            )
        }

        override fun onError(exception: Exception) {
            report(
                attempt,
                listener,
                SpeechRecognitionEvent.Error(
                    if (exception is SecurityException) {
                        SpeechRecognitionError.PERMISSION_DENIED
                    } else {
                        SpeechRecognitionError.SERVICE_UNAVAILABLE
                    },
                ),
            )
        }

        override fun onTimeout() {
            report(
                attempt,
                listener,
                SpeechRecognitionEvent.Error(SpeechRecognitionError.NO_MATCH),
            )
        }
    }

    private fun report(
        attempt: Long,
        listener: (SpeechRecognitionEvent) -> Unit,
        event: SpeechRecognitionEvent,
    ) {
        val canReport = synchronized(lock) {
            !closed && attempt == generation
        }
        if (canReport) listener(event)
    }

    private fun detachListeningLocked(): ActiveListening =
        ActiveListening(
            service = speechService.also { speechService = null },
            recognizer = recognizer.also { recognizer = null },
        )

    private fun stopListening(active: ActiveListening) {
        runCatching { active.service?.cancel() }
        runCatching { active.service?.shutdown() }
        runCatching { active.recognizer?.close() }
    }

    companion object {
        private const val MODEL_ASSET_PATH = "vosk-model-small-cn-0.22"
        private const val MODEL_STORAGE_PATH = "vosk-model-cn"
        private const val SAMPLE_RATE = 16_000.0f
        private const val LISTENING_TIMEOUT_MILLIS = 10_000
    }

    private data class ActiveListening(
        val service: SpeechService?,
        val recognizer: Recognizer?,
    )
}

internal fun parseVoskText(json: String, key: String): String =
    runCatching { JSONObject(json).optString(key).trim() }
        .getOrDefault("")
