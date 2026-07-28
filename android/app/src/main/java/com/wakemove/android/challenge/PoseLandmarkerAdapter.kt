package com.wakemove.android.challenge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface PoseLandmarkerPlatform {
    fun newAnalysisExecutor(): ExecutorService

    fun cameraProviderFuture(context: Context): ListenableFuture<ProcessCameraProvider>
}

class PoseLandmarkerAdapter(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    private val platform: PoseLandmarkerPlatform = AndroidPoseLandmarkerPlatform,
) : PoseLandmarkSource {
    private val applicationContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(applicationContext)
    private val lock = Any()

    @Volatile
    private var listener: ((PoseObservation) -> Unit)? = null

    @Volatile
    private var poseLandmarker: PoseLandmarker? = null

    @Volatile
    private var closed = false

    private var started = false
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val frameGate = OwnedFrameGate()

    override fun start(listener: (PoseObservation) -> Unit) {
        synchronized(lock) {
            check(!closed) { "PoseLandmarkerAdapter is closed" }
            check(!started) { "PoseLandmarkerAdapter is already started" }
            started = true
            this.listener = listener
        }

        val executor = runAdapterOperation(::reportUnavailable, platform::newAnalysisExecutor)
            ?: return
        val published = synchronized(lock) {
            if (closed) {
                false
            } else {
                analysisExecutor = executor
                true
            }
        }
        if (!published) {
            executor.shutdown()
            return
        }

        val submitted = runAdapterOperation(::reportUnavailable) {
            executor.execute(::createPoseLandmarker)
            true
        } ?: false
        if (!submitted) {
            releaseFailedExecutor(executor)
            return
        }

        val providerFuture = runAdapterOperation(::reportUnavailable) {
            platform.cameraProviderFuture(applicationContext)
        } ?: return
        runAdapterOperation(::reportUnavailable) {
            providerFuture.addListener(
                {
                    if (closed) return@addListener
                    val provider = runAdapterOperation(::reportUnavailable) {
                        providerFuture.get()
                    } ?: return@addListener
                    cameraProvider = provider
                    bindCamera(provider, executor)
                },
                mainExecutor,
            )
        }
    }

    override fun close() {
        val resources = synchronized(lock) {
            if (closed) return
            closed = true
            listener = null
            AdapterResources(
                provider = cameraProvider,
                preview = preview,
                imageAnalysis = imageAnalysis,
                executor = analysisExecutor,
            ).also {
                cameraProvider = null
                preview = null
                imageAnalysis = null
                analysisExecutor = null
            }
        }

        resources.imageAnalysis?.clearAnalyzer()
        frameGate.close()
        mainExecutor.execute {
            val useCases = listOfNotNull(resources.preview, resources.imageAnalysis).toTypedArray()
            if (useCases.isNotEmpty()) resources.provider?.unbind(*useCases)
        }
        resources.executor?.execute {
            poseLandmarker?.close()
            poseLandmarker = null
        }
        resources.executor?.shutdown()
    }

    @SuppressLint("MissingPermission")
    private fun bindCamera(
        provider: ProcessCameraProvider,
        executor: ExecutorService,
    ) {
        if (closed) return
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(MAX_ANALYSIS_WIDTH, MAX_ANALYSIS_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
            .also {
                it.setAnalyzer(executor) { image ->
                    analyzeAndClose(image, ::analyze)
                }
            }

        synchronized(lock) {
            if (closed) {
                imageAnalysis.clearAnalyzer()
                return
            }
            this.preview = preview
            this.imageAnalysis = imageAnalysis
        }
        val camera = runAdapterOperation(::reportUnavailable) {
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        }
        if (camera == null) {
            imageAnalysis.clearAnalyzer()
            synchronized(lock) {
                if (this.preview === preview) this.preview = null
                if (this.imageAnalysis === imageAnalysis) this.imageAnalysis = null
            }
        }
    }

    private fun createPoseLandmarker() {
        val created = runAdapterOperation(::reportUnavailable) {
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .build(),
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputSegmentationMasks(false)
                .setResultListener(::onResult)
                .setErrorListener {
                    frameGate.release()
                    reportUnavailable()
                }
                .build()
            PoseLandmarker.createFromOptions(applicationContext, options)
        } ?: return
        synchronized(lock) {
            if (closed) {
                created.close()
            } else {
                poseLandmarker = created
            }
        }
    }

    private fun analyze(image: ImageProxy) {
        if (isLowLight(image.averageLuminanceSamples())) {
            listener?.invoke(PoseObservation.LowLight)
            return
        }
        if (!frameGate.tryReserve()) return
        var sourceBitmap: Bitmap? = null
        var orientedBitmap: Bitmap? = null
        try {
            val bitmap = createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            sourceBitmap = bitmap
            bitmap.copyPixelsFromBuffer(image.planes[0].buffer.duplicate().apply { rewind() })
            val matrix = Matrix().apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    postScale(-1f, 1f, image.width.toFloat(), image.height.toFloat())
                }
            }
            val oriented = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true,
            )
            orientedBitmap = oriented
            val mpImage = BitmapImageBuilder(oriented).build()
            frameGate.attach(OwnedInferenceFrame(mpImage, bitmap, oriented))
            sourceBitmap = null
            orientedBitmap = null
            val landmarker = poseLandmarker
            if (landmarker == null) {
                frameGate.release()
                return
            }
            landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (error: Exception) {
            frameGate.release()
            if (orientedBitmap?.isRecycled == false) orientedBitmap.recycle()
            if (sourceBitmap !== orientedBitmap && sourceBitmap?.isRecycled == false) {
                sourceBitmap.recycle()
            }
            reportUnavailable()
        }
    }

    private fun onResult(
        result: PoseLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: MPImage,
    ) {
        frameGate.release()
        val detected = result.landmarks().firstOrNull()
        if (detected == null || detected.size != PoseLandmark.entries.size) {
            listener?.invoke(PoseObservation.NoPerson)
            return
        }
        val landmarks = PoseLandmark.entries.associateWith { landmark ->
            val detectedLandmark = detected[landmark.ordinal]
            Landmark(
                x = detectedLandmark.x(),
                y = detectedLandmark.y(),
                z = detectedLandmark.z(),
                visibility = detectedLandmark.visibility().orElse(0f),
            )
        }
        listener?.invoke(
            PoseObservation.Frame(
                PoseFrame(
                    timestampMs = result.timestampMs(),
                    landmarks = landmarks,
                ),
            ),
        )
    }

    private fun reportUnavailable() {
        listener?.invoke(PoseObservation.NoPerson)
    }

    private fun releaseFailedExecutor(executor: ExecutorService) {
        val owned = synchronized(lock) {
            if (analysisExecutor === executor) {
                analysisExecutor = null
                true
            } else {
                false
            }
        }
        if (owned) executor.shutdown()
    }

    private data class AdapterResources(
        val provider: ProcessCameraProvider?,
        val preview: Preview?,
        val imageAnalysis: ImageAnalysis?,
        val executor: ExecutorService?,
    )

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
        const val MAX_ANALYSIS_WIDTH = 640
        const val MAX_ANALYSIS_HEIGHT = 480
    }
}

internal class OwnedFrameGate {
    private var reserved = false
    private var owned: AutoCloseable? = null
    private var closed = false

    val hasInFlight: Boolean
        get() = synchronized(this) { reserved }

    fun tryReserve(): Boolean = synchronized(this) {
        if (closed || reserved) return false
        reserved = true
        true
    }

    fun attach(resource: AutoCloseable) {
        synchronized(this) {
            check(reserved && owned == null && !closed)
            owned = resource
        }
    }

    fun tryAcquire(resource: AutoCloseable): Boolean {
        if (!tryReserve()) {
            resource.close()
            return false
        }
        attach(resource)
        return true
    }

    fun release() {
        val resource = synchronized(this) {
            val current = owned
            owned = null
            reserved = false
            current
        }
        resource?.close()
    }

    fun close() {
        val resource = synchronized(this) {
            closed = true
            reserved = false
            val current = owned
            owned = null
            current
        }
        resource?.close()
    }
}

private class OwnedInferenceFrame(
    private val image: MPImage,
    private val source: Bitmap,
    private val oriented: Bitmap,
) : AutoCloseable {
    override fun close() {
        image.close()
        if (!oriented.isRecycled) oriented.recycle()
        if (source !== oriented && !source.isRecycled) source.recycle()
    }
}

internal inline fun <T : AutoCloseable> analyzeAndClose(frame: T, analyze: (T) -> Unit) {
    try {
        analyze(frame)
    } finally {
        frame.close()
    }
}

internal inline fun <T> runAdapterOperation(
    onUnavailable: () -> Unit,
    operation: () -> T,
): T? = try {
    operation()
} catch (_: Exception) {
    onUnavailable()
    null
}

internal fun isLowLight(lumaSamples: ByteArray): Boolean {
    if (lumaSamples.isEmpty()) return true
    val average = lumaSamples.sumOf { it.toUByte().toInt() } / lumaSamples.size
    return average < LOW_LIGHT_LUMA_THRESHOLD
}

private fun ImageProxy.averageLuminanceSamples(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer.duplicate()
    val sampleColumns = 16
    val sampleRows = 16
    val samples = ByteArray(sampleColumns * sampleRows)
    var index = 0
    repeat(sampleRows) { sampleRow ->
        val y = sampleRow * (height - 1) / (sampleRows - 1)
        repeat(sampleColumns) { sampleColumn ->
            val x = sampleColumn * (width - 1) / (sampleColumns - 1)
            val pixelOffset = y * plane.rowStride + x * plane.pixelStride
            samples[index++] = buffer.rgbLuminance(pixelOffset)
        }
    }
    return samples
}

private fun ByteBuffer.rgbLuminance(offset: Int): Byte {
    val red = get(offset).toUByte().toInt()
    val green = get(offset + 1).toUByte().toInt()
    val blue = get(offset + 2).toUByte().toInt()
    return ((red * 77 + green * 150 + blue * 29) shr 8).toByte()
}

private const val LOW_LIGHT_LUMA_THRESHOLD = 50

private object AndroidPoseLandmarkerPlatform : PoseLandmarkerPlatform {
    override fun newAnalysisExecutor(): ExecutorService = Executors.newSingleThreadExecutor()

    override fun cameraProviderFuture(
        context: Context,
    ): ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(context)
}
