package it.eciavatta.mqttsensors

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.forEach
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.util.*


class MainActivity : AppCompatActivity() {

    private var buttonConnect: Button? = null
    private var buttonDisconnect: Button? = null
    private var textHost: EditText?  = null
    private var textPort: EditText?  = null
    private var textInterval: EditText? = null
    private var labelsStatus: TextView? = null
    private var switches: LinearLayout? = null

    private val clientId = "MqttSensors"

    private var reader: SensorReader? = null
    private val activeSensors: MutableList<String> = LinkedList()

    private var client: MqttAndroidClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        reader = SensorReader(getSystemService(Context.SENSOR_SERVICE) as SensorManager)

        buttonConnect = findViewById(R.id.button_connect)
        buttonDisconnect = findViewById(R.id.button_disconnect)
        textHost = findViewById(R.id.text_host)
        textPort = findViewById(R.id.text_port)
        textInterval = findViewById(R.id.text_interval)
        labelsStatus = findViewById(R.id.label_status)
        switches = findViewById(R.id.switches)

        buttonConnect!!.setOnClickListener {
            buttonConnect!!.isEnabled = false
            connect()
        }
        buttonDisconnect!!.setOnClickListener {
            buttonDisconnect!!.isEnabled = false
            stopReading()
            disconnect()
        }
    }

    private fun connect() {
        val serverUri = "tcp://${textHost!!.text}:${textPort!!.text}"
        client = MqttAndroidClient(applicationContext, serverUri, clientId)
        client?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                if (reconnect) {
                    labelsStatus!!.text = "Reconnected to $serverURI"
                } else {
                    labelsStatus!!.text = "Connected to $serverURI"
                }
                buttonDisconnect!!.isEnabled = true
                startReading()
            }

            override fun connectionLost(cause: Throwable?) {
                labelsStatus!!.text = "Lost connection"
                buttonDisconnect!!.isEnabled = false
                stopReading()
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                labelsStatus!!.text = "Incoming message: ${message.payload}"
            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {
            }
        })

        val options = MqttConnectOptions()
        options.isAutomaticReconnect = true
        options.isCleanSession = false

        client?.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                val disconnectedBufferOptions = DisconnectedBufferOptions()
                disconnectedBufferOptions.isBufferEnabled = true
                disconnectedBufferOptions.bufferSize = 100
                disconnectedBufferOptions.isPersistBuffer = false
                disconnectedBufferOptions.isDeleteOldestMessages = false
                client?.setBufferOpts(disconnectedBufferOptions)
            }

            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                labelsStatus!!.text = "Failed to connect to $serverUri"
                exception.printStackTrace()
            }
        })
    }

    private fun disconnect() {
        client?.disconnect(null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                labelsStatus!!.text = "Disconnected"
                buttonConnect!!.isEnabled = true
            }

            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                labelsStatus!!.text = "Failed to disconnect: ${exception.message}"
                exception.printStackTrace()
            }
        })
    }

    private fun startReading() {
        switches!!.forEach {
            val switch = it as Switch
            if (switch.isChecked) {
                val sensorType = switch.text.toString()
                reader!!.startRead(sensorType, textInterval!!.text.toString().toInt()) { m ->
                    publishMessage(m, sensorType)
                }
                activeSensors.add(sensorType)
            }
        }
    }

    private fun stopReading() {
        activeSensors.forEach {
            reader!!.stopRead(it)
        }
        activeSensors.clear()
    }

    private fun publishMessage(message: JSONObject, topic: String) {
        try {
            val mqttMessage = MqttMessage(message.toString().toByteArray())
            client?.publish(topic, mqttMessage)
        } catch (e: MqttException) {
            System.err.println("Error Publishing: " + e.message)
            e.printStackTrace()
        }
    }

}
