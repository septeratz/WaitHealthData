package com.example.waithealthdata.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.*

data class SensorData(
    val timestamp: String,
    val heart_rate: Int?,
    val drink_count: Int?,
    val elapsed_time: Long,
    val user_state: String,
    val drink_amount: Int?,
    val alcohol_percentage: Float?
)

interface ApiService {
    @POST("sensor_data")
    fun sendSensorData(@Body data: SensorData): Call<Void>
}

class MainActivity : Activity(), SensorEventListener {

    private var isSendingData = false
    private val handler = Handler(Looper.getMainLooper())
    private val interval = 30 * 1000L

    private lateinit var dataClient: DataClient
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var skinTemperatureSensor: Sensor? = null
    private var currentHeartRate: Float = -1f

    private var accelerometerSensor: Sensor? = null
    private var drinkCount = 0
    private var lastTiltTime = 0L
    private var lastShakeTime = 0L
    private val TILT_ANGLE = 60f
    private val SHAKE_THRESHOLD = 15f
    private val COOLDOWN_MS = 10000L

    private val BODY_SENSORS_PERMISSION_REQUEST_CODE = 1

    private lateinit var sendDataButton: Button
    private lateinit var confirmButton: Button
    private lateinit var userStateSpinner: Spinner
    private lateinit var alcoholInput: EditText
    private lateinit var dataInputLayout: LinearLayout
    private lateinit var sensorSettingView: View
    private lateinit var resetSensorButton: Button
    private lateinit var saveSensorButton: Button
    private lateinit var toggleViewButton: ImageButton

    private var initTime: Long = 0L
    private val baseTimeStack = ArrayDeque<Long>()
    private var baseTime: Long = 0L

    private var baselineGravity: FloatArray? = null
    private var baselineGyro: FloatArray? = null
    private var savedGravity: FloatArray? = null
    private var savedGyro: FloatArray? = null
    private var currentGravity: FloatArray? = null
    private var currentGyro: FloatArray? = null
    private val ALPHA = 0.1f
    private var tiltStartTime = -1L
    private val TILT_HOLD_THRESHOLD = 800L
    private val DRINK_MATCH_ANGLE = 20.0
    private var lastDrinkTime = 0L

    private fun lowPassFilter(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] + ALPHA * (input[i] - output[i])
        }
        return output
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        skinTemperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

        sendDataButton = findViewById(R.id.sendDataButton)
        confirmButton = findViewById(R.id.confirmButton)
        userStateSpinner = findViewById(R.id.userStateSpinner)
        alcoholInput = findViewById(R.id.alcoholInput)
        dataInputLayout = findViewById(R.id.dataInputLayout)
        resetSensorButton = findViewById(R.id.resetSensorButton)
        saveSensorButton = findViewById(R.id.saveSensorButton)
        sensorSettingView = findViewById(R.id.sensorSettingView)
        toggleViewButton = findViewById(R.id.toggleViewButton)

        dataInputLayout.visibility = View.GONE
        sensorSettingView.visibility = View.GONE

        sendDataButton.setOnClickListener {
            dataInputLayout.visibility = View.VISIBLE
            findViewById<TextView>(R.id.drinkCountTextView).bringToFront()
        }

        findViewById<Button>(R.id.layoutDisableButton).setOnClickListener {
            dataInputLayout.visibility = View.INVISIBLE
        }

        toggleViewButton.setOnClickListener {
            sensorSettingView.visibility = View.VISIBLE
            findViewById<TextView>(R.id.drinkCountTextView).bringToFront()
        }

        confirmButton.setOnClickListener {
            if (!isSendingData) {
                isSendingData = true
                confirmButton.text = "전송 중지"
                initTime = System.currentTimeMillis()
                baseTimeStack.clear()
                baseTime = initTime
                startDataSending()
            } else {
                isSendingData = false
                confirmButton.text = "확인"
                handler.removeCallbacksAndMessages(null)
            }
        }

        resetSensorButton.setOnClickListener {
            resetSensorValues()
            Toast.makeText(this, "센서 세팅값이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }

        saveSensorButton.setOnClickListener {
            saveSensorValues()
        }

        val states = listOf("평상시", "음주 중")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, states)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userStateSpinner.adapter = adapter

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                BODY_SENSORS_PERMISSION_REQUEST_CODE
            )
        } else {
            startSensorMonitoring()
        }

        dataClient = Wearable.getDataClient(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {}

    private fun resetSensorValues() {}

    private fun saveSensorValues() {}

    private fun startSensorMonitoring() {}

    private fun startDataSending() {}

    private fun sendDataToAWS() {}
}
