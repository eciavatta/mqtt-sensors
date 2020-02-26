package it.eciavatta.mqttsensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.json.JSONObject


class SensorReader(private val sensorManager: SensorManager) {

    private val listeners: MutableMap<Int, SensorEventListener> = HashMap()
    private val oldValues: MutableMap<Int, MutableList<FloatArray>> = HashMap()
    private val lastUpdates: MutableMap<Int, Long> = HashMap()

    fun startRead(sensorType: String, interval: Int, callback: (JSONObject) -> Unit) {
        val sensorIntType = sensorIntType(sensorType)
        if (listeners.containsKey(sensorIntType)) {
            throw IllegalStateException("There is another listener for sensor of type $sensorType")
        }

        val sensor = sensorManager.getDefaultSensor(sensorIntType)

        val listener = object: SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Nope
            }

            override fun onSensorChanged(event: SensorEvent?) {
                sensorChanged(event!!.sensor.type, interval, event.values, callback)
            }
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        listeners[sensorIntType] = listener
        oldValues[sensorIntType] = ArrayList()
        lastUpdates[sensorIntType] = System.currentTimeMillis()
    }

    fun stopRead(sensorType: String) {
        val sensorIntType = sensorIntType(sensorType)
        if (!listeners.containsKey(sensorIntType)) {
            throw IllegalStateException("No listener for type $sensorType")
        }

        sensorManager.unregisterListener(listeners.remove(sensorIntType))
        oldValues.remove(sensorIntType)
        lastUpdates.remove(sensorIntType)
    }

    private fun sensorIntType(sensorType: String): Int = when (sensorType) {
        "ACCELEROMETER" -> Sensor.TYPE_ACCELEROMETER
        "ACCELEROMETER_UNCALIBRATED" -> Sensor.TYPE_ACCELEROMETER_UNCALIBRATED
        "GRAVITY" -> Sensor.TYPE_GRAVITY
        "GYROSCOPE" -> Sensor.TYPE_GYROSCOPE
        "GYROSCOPE_UNCALIBRATED" -> Sensor.TYPE_GYROSCOPE_UNCALIBRATED
        "LINEAR_ACCELERATION" -> Sensor.TYPE_LINEAR_ACCELERATION
        "ROTATION_VECTOR" -> Sensor.TYPE_ROTATION_VECTOR
        "STEP_COUNTER" -> Sensor.TYPE_STEP_COUNTER
        else -> throw IllegalArgumentException("Invalid type: $sensorType")
    }

    private fun sensorChanged(sensorIntType: Int, interval: Int, sensorValues: FloatArray,
                              callback: (JSONObject) -> Unit) {


        val lastUpdate = lastUpdates[sensorIntType]!!
        val now = System.currentTimeMillis()
        val old = oldValues[sensorIntType]!!
        old.add(sensorValues)

        if (now - lastUpdate > interval) {
            val values = old.reduce { a, b ->
                a.zip(b) { xa, xb -> xa + xb }.toFloatArray()
            }.map { x -> x / old.size }

            old.clear()
            lastUpdates[sensorIntType] = now
            sensorUpdated(sensorIntType, values, now, callback)
        }
    }

    private fun sensorUpdated(sensorIntType: Int, sensorValues: List<Float>, timestamp: Long,
                              callback: (JSONObject) -> Unit) {
        val json = JSONObject()
        val jsonValues = JSONObject()

        if (sensorIntType == Sensor.TYPE_STEP_COUNTER) {
            jsonValues.put("steps", sensorValues[0])
        } else {
            jsonValues.put("x_axis", sensorValues[0])
            jsonValues.put("y_axis", sensorValues[1])
            jsonValues.put("z_axis", sensorValues[2])

            if (sensorIntType == Sensor.TYPE_ACCELEROMETER_UNCALIBRATED) {
                jsonValues.put("x_axis_bias", sensorValues[3])
                jsonValues.put("y_axis_bias", sensorValues[4])
                jsonValues.put("z_axis_bias", sensorValues[5])
            }

            if (sensorIntType == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
                jsonValues.put("x_axis_drift", sensorValues[3])
                jsonValues.put("y_axis_drift", sensorValues[4])
                jsonValues.put("z_axis_drift", sensorValues[5])
            }

            if (sensorIntType == Sensor.TYPE_ROTATION_VECTOR) {
                jsonValues.put("scalar", sensorValues[3])
            }
        }

        json.put("values", jsonValues)
        json.put("timestamp", timestamp)

        callback(json)
    }

}
