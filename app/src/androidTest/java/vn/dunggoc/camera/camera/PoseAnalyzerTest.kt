package vn.dunggoc.camera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import vn.dunggoc.guidance.Code
import vn.dunggoc.guidance.Guidance
import vn.dunggoc.guidance.Preferences
import java.util.concurrent.atomic.AtomicReference

/** Runs the bundled model and real bitmap lifecycle on an Android device/emulator. */
@RunWith(AndroidJUnit4::class)
class PoseAnalyzerTest {
    @Test
    fun releasedBitmapDoesNotDisableGuidanceOnFirstOrFollowingFrame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val failure = AtomicReference<Throwable?>(null)
        val received = mutableListOf<Guidance>()
        val analyzer = PoseAnalyzer(context, AtomicReference(Preferences()), AtomicReference<Float?>(null),
            { received.add(it) }, { failure.set(it) })
        try {
            repeat(2) { index ->
                // Above the analyzer's maximum throttle interval; covers continuing
                // after the first inference rather than silently staying disabled.
                SystemClock.sleep(600)
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                val info = mock(ImageInfo::class.java)
                `when`(info.rotationDegrees).thenReturn(0)
                val frame = mock(ImageProxy::class.java)
                `when`(frame.toBitmap()).thenReturn(bitmap)
                `when`(frame.cropRect).thenReturn(Rect(0, 0, 64, 64))
                `when`(frame.imageInfo).thenReturn(info)

                analyzer.analyze(frame)

                assertNull(failure.get()?.stackTraceToString(), failure.get())
                assertEquals(index + 1, received.size)
                assertEquals(Code.NO_PERSON, received.last().code)
                assertTrue("Frame bitmap must be released after processing", bitmap.isRecycled)
                verify(frame).close()
            }
        } finally {
            analyzer.close()
        }
    }
}
