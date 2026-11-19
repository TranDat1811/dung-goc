package vn.dunggoc.camera.camera

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Rational
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import vn.dunggoc.guidance.Guidance
import vn.dunggoc.guidance.Preferences
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class CameraSession(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    width: Int,
    height: Int,
    rotation: Int,
    preferences: AtomicReference<Preferences>,
    roll: AtomicReference<Float?>,
    onGuidance: (Guidance) -> Unit,
    private val onCameraReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val onAnalysisError: () -> Unit,
) : Closeable {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var capture: ImageCapture? = null
    private val analyzer = PoseAnalyzer(context.applicationContext, preferences, roll, { guide ->
        mainExecutor.execute { if (!closed) onGuidance(guide) }
    }, { error ->
        Log.e("DungGoc", "Pose analysis failed", error)
        mainExecutor.execute { if (!closed) onAnalysisError() }
    })

    init {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!closed) {
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        throw SecurityException("Camera permission is not granted")
                    }
                    val cameraProvider = future.get()
                    check(cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) { "No back camera" }
                    val previewCase = Preview.Builder().setTargetRotation(rotation).build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysisCase = ImageAnalysis.Builder()
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        ).build())
                        .build().also { it.setAnalyzer(worker, analyzer) }
                    val captureCase = ImageCapture.Builder().setTargetRotation(rotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    provider = cameraProvider
                    preview = previewCase
                    analysis = analysisCase
                    capture = captureCase
                    val group = UseCaseGroup.Builder()
                        .setViewPort(ViewPort.Builder(Rational(width, height), rotation)
                            .setScaleType(ViewPort.FILL_CENTER).build())
                        .addUseCase(previewCase).addUseCase(analysisCase).addUseCase(captureCase).build()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                    onCameraReady()
                } catch (error: Exception) {
                    Log.e("DungGoc", "Cannot bind camera", error)
                    onError(if (error is SecurityException) "Hãy bật quyền camera trong cài đặt rồi mở lại ứng dụng."
                        else "Chưa mở được camera. Hãy đóng ứng dụng đang dùng camera rồi thử lại.")
                }
            }
        }, mainExecutor)
    }

    fun takePhoto(file: File, onSaved: (File) -> Unit, onFailure: () -> Unit) {
        val imageCapture = capture
        if (closed || imageCapture == null) {
            file.delete()
            onFailure()
            return
        }
        try {
            imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (closed) file.delete() else onSaved(file)
                }
                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    Log.e("DungGoc", "Capture failed", exception)
                    if (!closed) onFailure()
                }
            })
        } catch (error: Exception) {
            file.delete()
            Log.e("DungGoc", "Cannot start capture", error)
            if (!closed) onFailure()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        analysis?.clearAnalyzer()
        // Unbind only our use cases, never another session's camera.
        listOfNotNull(preview, analysis, capture).takeIf { it.isNotEmpty() }?.let {
            provider?.unbind(*it.toTypedArray())
        }
        worker.execute { runCatching { analyzer.close() }.onFailure { Log.e("DungGoc", "Cannot close analyzer", it) } }
        worker.shutdown()
        capture = null
    }
}

