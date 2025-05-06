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
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity(), SensorEventListener {

    private var isSendingData = false
    private val handler = Handler(Looper.getMainLooper())
    private val interval = 30 * 1000L

    private lateinit var dataClient: DataClient
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var skinTemperatureSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null

    private lateinit var sendDataButton: Button
    private lateinit var userStateSpinner: Spinner
    private lateinit var dataInputLayout: LinearLayout
    private lateinit var sensorSettingView: View
    private lateinit var resetSensorButton: Button
    private lateinit var saveSensorButton: Button
    private lateinit var mainWrapper: LinearLayout
    private lateinit var drinkCountTextView2: TextView
    private lateinit var toggleViewButton: Button

    private var drinkCount = 0

    private val BODY_SENSORS_PERMISSION_REQUEST_CODE = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        skinTemperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

        sendDataButton = findViewById(R.id.sendDataButton)
        userStateSpinner = findViewById(R.id.userStateSpinner)
        dataInputLayout = findViewById(R.id.dataInputLayout)
        resetSensorButton = findViewById(R.id.resetSensorButton)
        saveSensorButton = findViewById(R.id.saveSensorButton)
        sensorSettingView = findViewById(R.id.sensorSettingView)
        mainWrapper = findViewById(R.id.mainWrapper)
        drinkCountTextView2 = findViewById(R.id.drinkCountTextView2)
        toggleViewButton = findViewById(R.id.toggleViewButton)

        dataInputLayout.visibility = View.GONE
        sensorSettingView.visibility = View.GONE

        // Spinner 설정 (음주X / 음주중)
        val states = listOf("상태설정", "음주X", "음주중")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, states)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userStateSpinner.adapter = adapter
        userStateSpinner.setSelection(0)



        sendDataButton.setOnClickListener {
            // 메인 화면 요소 숨기고 측정 화면 표시
            mainWrapper.visibility = View.GONE
            dataInputLayout.visibility = View.VISIBLE
            drinkCountTextView2.text = "Drinks: $drinkCount"
        }

        toggleViewButton.setOnClickListener {
            sensorSettingView.visibility = View.VISIBLE
        }

        resetSensorButton.setOnClickListener {
            Toast.makeText(this, "센서 초기화됨", Toast.LENGTH_SHORT).show()
        }

        saveSensorButton.setOnClickListener {
            Toast.makeText(this, "센서값 저장됨", Toast.LENGTH_SHORT).show()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
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

    private fun startSensorMonitoring() {
        // 센서 시작 로직 필요 시 여기에
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent?) {}
}
