package com.dhikr.app.core.counter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Android-facing half of the auto-counter feature (plan.md §40): registers a
 * [SensorEventListener] on the device accelerometer and forwards each sample's
 * magnitude into an [AutoCounterDetector], invoking [onFlick] on the samples
 * it identifies as a deliberate wrist-flick. All detection logic lives in the
 * pure, unit-tested [AutoCounterDetector] — this class only owns the
 * SensorManager registration lifecycle.
 *
 * No runtime permission is required for TYPE_ACCELEROMETER (normal-protection
 * sensor), and SENSOR_DELAY_GAME keeps the sampling rate — and battery cost —
 * modest rather than requesting the fastest rate. The listener is registered
 * only between [start] and [stop]; callers (CounterScreen) tie those to the
 * composable's lifecycle so nothing samples the sensor once the counter
 * screen is gone or the feature is toggled off, per §40's "do not let it
 * affect normal counter performance" / battery requirements.
 */
class AutoCounterSensorListener(context: Context) {
    private val sensorManager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = AutoCounterDetector(threshold = FLICK_THRESHOLD, debounceMillis = DEBOUNCE_MILLIS)

    private var listener: SensorEventListener? = null

    /** True if this device actually has an accelerometer — callers should hide
     *  the settings toggle (or show it disabled) when false rather than let it
     *  silently do nothing. */
    val isSupported: Boolean get() = accelerometer != null

    fun start(onFlick: () -> Unit) {
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        stop() // guard against double-registration if start() is called twice
        detector.reset()
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)
                if (detector.onSample(magnitude, event.timestamp / 1_000_000L)) {
                    onFlick()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        listener = l
        manager.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        val manager = sensorManager ?: return
        listener?.let { manager.unregisterListener(it) }
        listener = null
    }

    private companion object {
        // m/s² above resting gravity (~9.8) a sample must cross to count as a
        // flick. Tuned loose rather than tight: a missed flick just means the
        // user taps manually (the always-available primary input); a false
        // positive over-counts, which is the worse failure for a counter.
        const val FLICK_THRESHOLD = 16f
        const val DEBOUNCE_MILLIS = 450L
    }
}
