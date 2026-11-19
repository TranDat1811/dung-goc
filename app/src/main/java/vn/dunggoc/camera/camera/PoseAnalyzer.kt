package vn.dunggoc.camera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import vn.dunggoc.guidance.Body
import vn.dunggoc.guidance.CameraAnalysis
import vn.dunggoc.guidance.CompositionCoach
import vn.dunggoc.guidance.SceneComposition
import vn.dunggoc.guidance.CaptureMode
import vn.dunggoc.guidance.Observation
import vn.dunggoc.guidance.Point
import vn.dunggoc.guidance.Preferences
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

/** Created, called and closed on the same single-thread executor. */
class PoseAnalyzer(
    private val context: Context,
    private val preferences: AtomicReference<Preferences>,
    private val roll: AtomicReference<Float?>,
    private val onGuidance: (CameraAnalysis) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {
    private var landmarker: PoseLandmarker? = null
    private val coach = CompositionCoach()
    private var lastFrame = -1L
    private var intervalMs = 100L
    private var failed = false
    private var lastMode: CaptureMode? = null

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        val currentPreferences = preferences.get()
        if (currentPreferences.captureMode != lastMode) {
            failed = false
            lastMode = currentPreferences.captureMode
        }
        if (failed || now - lastFrame < intervalMs) {
            image.close()
            return
        }
        lastFrame = now
        var source: Bitmap? = null
        var cropped: Bitmap? = null
        var upright: Bitmap? = null
        try {
            // All use cases share a CameraX ViewPort. Crop BEFORE rotation so the
            // analyzed bitmap, the visible preview and the saved photo agree.
            val sourceBitmap = image.toBitmap()
            source = sourceBitmap
            val crop = image.cropRect
            val croppedBitmap = Bitmap.createBitmap(sourceBitmap, crop.left, crop.top, crop.width(), crop.height())
            cropped = croppedBitmap
            val bitmap = Bitmap.createBitmap(croppedBitmap, 0, 0, croppedBitmap.width, croppedBitmap.height,
                Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, false)
            upright = bitmap
            // MPImage.close() recycles the wrapped bitmap. Read every pixel and
            // dimension we need before handing ownership to MediaPipe.
            val aspectRatio = bitmap.width.toFloat() / bitmap.height
            val scene = sampleScene(bitmap)
            // Explicit scene mode only needs image structure, so it remains usable
            // without loading the pose model or its native inference runtime.
            val bodies = if (currentPreferences.captureMode == CaptureMode.SCENE) emptyList() else {
                val detector = landmarker ?: PoseLandmarker.createFromOptions(
                    context,
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                        .setRunningMode(RunningMode.VIDEO)
                        .setNumPoses(2)
                        .setMinPoseDetectionConfidence(0.6f)
                        .setMinPosePresenceConfidence(0.6f)
                        .setMinTrackingConfidence(0.6f)
                        .build(),
                ).also { landmarker = it }
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = try { detector.detectForVideo(mpImage, now) } finally { mpImage.close() }
                result.landmarks().map { landmarks ->
                    Body(landmarks.map { point ->
                        Point(point.x(), point.y(), minOf(point.visibility().orElse(0f), point.presence().orElse(0f)))
                    })
                }
            }
            onGuidance(coach.update(Observation(bodies, aspectRatio,
                roll.get(), scene.brightness), scene, currentPreferences, now))
            // Never queue inference work. Slow devices analyze less frequently.
            val elapsed = SystemClock.uptimeMillis() - now
            intervalMs = (elapsed * 2).coerceIn(100L, 500L)
        } catch (error: Exception) {
            failed = true
            onError(error)
        } catch (error: LinkageError) {
            // Missing native support must leave manual capture usable.
            failed = true
            onError(error)
        } finally {
            listOfNotNull(upright, cropped, source).distinct().filterNot { it.isRecycled }.forEach { it.recycle() }
            image.close()
        }
    }

    private fun sampleScene(bitmap: Bitmap): vn.dunggoc.guidance.SceneFeatures {
        val scale = 64f / maxOf(bitmap.width, bitmap.height)
        val thumbnail = Bitmap.createScaledBitmap(bitmap, maxOf(3, (bitmap.width * scale).toInt()),
            maxOf(3, (bitmap.height * scale).toInt()), false)
        try {
            val pixels = IntArray(thumbnail.width * thumbnail.height)
            thumbnail.getPixels(pixels, 0, thumbnail.width, 0, 0, thumbnail.width, thumbnail.height)
            return SceneComposition.analyze(pixels, thumbnail.width, thumbnail.height)
        } finally {
            if (thumbnail !== bitmap) thumbnail.recycle()
        }
    }

    override fun close() {
        landmarker?.close()
        landmarker = null
    }
}

