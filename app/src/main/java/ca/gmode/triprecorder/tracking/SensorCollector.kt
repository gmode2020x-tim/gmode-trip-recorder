package ca.gmode.triprecorder.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import ca.gmode.triprecorder.data.SensorSnapshot
import kotlin.math.sqrt

data class OrientationSnapshot(
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val magneticHeadingDegrees: Double? = null,
)

class SensorCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerationSensor = linearAccelerationSensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var pressureTotal = 0.0
    private var pressureSamples = 0
    private var accelerationSquaredTotal = 0.0
    private var accelerationSamples = 0
    private var accelerationPeak = 0.0
    private var accelerationPeakX = 0.0
    private var accelerationPeakY = 0.0
    private var accelerationPeakZ = 0.0
    private var gyroscopePeak = 0.0
    private val gravityEstimate = DoubleArray(3)
    private var gravityEstimateReady = false
    private var pitchDegrees: Double? = null
    private var rollDegrees: Double? = null
    private var magneticHeadingDegrees: Double? = null
    var onOrientationChanged: ((OrientationSnapshot) -> Unit)? = null
    var onLinearAccelerationChanged: ((VehicleShockVector) -> Unit)? = null

    fun start() {
        pressureSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerationSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscopeSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        rotationVectorSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    @Synchronized
    fun snapshotAndReset(): SensorSnapshot {
        val snapshot = SensorSnapshot(
            pressureHpa = if (pressureSamples > 0) pressureTotal / pressureSamples else null,
            accelerationRmsMs2 = if (accelerationSamples > 0) {
                sqrt(accelerationSquaredTotal / accelerationSamples)
            } else {
                null
            },
            accelerationPeakMs2 = accelerationPeak.takeIf { accelerationSamples > 0 },
            gyroscopePeakRadS = gyroscopePeak.takeIf { gyroscopePeak > 0 },
            accelerationPeakXMs2 = accelerationPeakX.takeIf { accelerationSamples > 0 },
            accelerationPeakYMs2 = accelerationPeakY.takeIf { accelerationSamples > 0 },
            accelerationPeakZMs2 = accelerationPeakZ.takeIf { accelerationSamples > 0 },
        )
        pressureTotal = 0.0
        pressureSamples = 0
        accelerationSquaredTotal = 0.0
        accelerationSamples = 0
        accelerationPeak = 0.0
        accelerationPeakX = 0.0
        accelerationPeakY = 0.0
        accelerationPeakZ = 0.0
        gyroscopePeak = 0.0
        return snapshot
    }

    @Synchronized
    fun orientation(): OrientationSnapshot = OrientationSnapshot(pitchDegrees, rollDegrees, magneticHeadingDegrees)

    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                pressureTotal += event.values[0].toDouble()
                pressureSamples += 1
            }

            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                val linear = linearAcceleration(event)
                val displayRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_90
                val vehicle = ShockAxisMath.toVehicleAxes(
                    rawXMs2 = linear[0],
                    rawYMs2 = linear[1],
                    rawZMs2 = linear[2],
                    displayRotation = displayRotation,
                )
                val linearMagnitude = vehicle.magnitudeMs2
                onLinearAccelerationChanged?.invoke(vehicle)
                accelerationSquaredTotal += linearMagnitude * linearMagnitude
                accelerationSamples += 1
                if (linearMagnitude > accelerationPeak) {
                    accelerationPeak = linearMagnitude
                    accelerationPeakX = vehicle.forwardMs2
                    accelerationPeakY = vehicle.rightMs2
                    accelerationPeakZ = vehicle.upMs2
                }
            }

            Sensor.TYPE_GYROSCOPE -> gyroscopePeak = maxOf(gyroscopePeak, vectorMagnitude(event.values))

            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotation = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val screenRotation = remapToScreen(rotation)
                val orientation = VehicleOrientationMath.fromWorldUp(
                    upX = screenRotation[6].toDouble(),
                    upY = screenRotation[7].toDouble(),
                    upZ = screenRotation[8].toDouble(),
                )
                pitchDegrees = orientation.pitchDegrees
                rollDegrees = orientation.rollDegrees
                // Android +Z points out through the screen. The phone is mounted with its back
                // facing forward, so vehicle-forward is the device -Z axis projected onto ENU.
                magneticHeadingDegrees = VehicleOrientationMath.headingDegrees(
                    east = -rotation[2].toDouble(),
                    north = -rotation[5].toDouble(),
                )
                onOrientationChanged?.invoke(orientation.copy(magneticHeadingDegrees = magneticHeadingDegrees))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun remapToScreen(rotationMatrix: FloatArray): FloatArray {
        val displayRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_90
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val remapped = FloatArray(9)
        return if (SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)) remapped else rotationMatrix
    }

    private fun linearAcceleration(event: SensorEvent): DoubleArray {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            return DoubleArray(3) { index -> event.values[index].toDouble() }
        }
        if (!gravityEstimateReady) {
            repeat(3) { index -> gravityEstimate[index] = event.values[index].toDouble() }
            gravityEstimateReady = true
            return DoubleArray(3)
        }
        return DoubleArray(3) { index ->
            val raw = event.values[index].toDouble()
            gravityEstimate[index] = GRAVITY_FILTER_ALPHA * gravityEstimate[index] +
                (1.0 - GRAVITY_FILTER_ALPHA) * raw
            raw - gravityEstimate[index]
        }
    }

    private fun vectorMagnitude(values: FloatArray): Double = sqrt(
        values.take(3).sumOf { value -> value.toDouble() * value.toDouble() },
    )

    private companion object {
        const val GRAVITY_FILTER_ALPHA = 0.8
    }
}
