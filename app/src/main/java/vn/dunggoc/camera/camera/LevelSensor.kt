package vn.dunggoc.camera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class LevelSensor(context: Context, rotation: Int, private val output: AtomicReference<Float?>) : Closeable {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var x = 0f
    private var y = 0f
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            x = x * 0.8f + event.values[0] * 0.2f
            y = y * 0.8f + event.values[1] * 0.2f
            val landscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            val horizontal = if (landscape) y else x
            val vertical = if (landscape) x else y
            // Near-horizontal phones have no reliable roll from gravity.
            output.set(if (hypot(x, y) < 3f) null else Math.toDegrees(atan2(abs(horizontal), abs(vertical)).toDouble()).toFloat())
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun close() {
        manager.unregisterListener(listener)
        output.set(null)
    }
}
