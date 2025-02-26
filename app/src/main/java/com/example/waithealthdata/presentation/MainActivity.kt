/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SensorData(
    val timestamp: String,
    val heart_rate: Int?,
    val drink_count: Int?,
    val user_state: String,
    val drink_amount: Int?,
    val alcohol_percentage: Float?
)

interface ApiService {
    @POST("sensor_data")
    fun sendSensorData(@Body data: SensorData): Call<Void>
}

class MainActivity : Activity(), SensorEventListener {

    private var isSendingData = false  // 🔹 전송 상태 플래그
    private val handler = Handler(Looper.getMainLooper())  // 🔹 1초 간격 실행을 위한 핸들러

    private val interval = 30 * 1000L // 30초 간격

    private lateinit var dataClient: DataClient
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var skinTemperatureSensor: Sensor? = null
    private var currentHeartRate: Float = -1f // 초기 심박수 값

    private var accelerometerSensor: Sensor? = null
    private var drinkCount = 0
    private var lastTiltTime = 0L
    private var lastShakeTime = 0L
    private val TILT_ANGLE = 60f // 기울기 감지 각도
    private val SHAKE_THRESHOLD = 15f // 흔들림 감지 임계값
    private val COOLDOWN_MS = 1000L // 중복 실행 방지 시간

    private val BODY_SENSORS_PERMISSION_REQUEST_CODE = 1

    private lateinit var sendDataButton: Button
    private lateinit var confirmButton: Button
    private lateinit var userStateSpinner: Spinner
    private lateinit var alcoholInput: EditText
    private lateinit var dataInputLayout: LinearLayout


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        // 센서 관리자 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor == null) {
            Log.e("Sensor", "Heart rate sensor is not available.")
            return
        }
        // 가속도계 센서 초기화
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        skinTemperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (skinTemperatureSensor == null) {
            Log.d("SensorCheck", "피부 온도 센서가 지원되지 않습니다.")
        } else {
            Log.d("SensorCheck", "피부 온도 센서가 지원됩니다.")
        }

        // UI 요소 초기화
        sendDataButton = findViewById(R.id.sendDataButton)
        confirmButton = findViewById(R.id.confirmButton)
        userStateSpinner = findViewById(R.id.userStateSpinner)
        alcoholInput = findViewById(R.id.alcoholInput)
        dataInputLayout = findViewById(R.id.dataInputLayout)

        // 기본적으로 숨김
        dataInputLayout.visibility = View.GONE

        // Spinner 설정
        val states = listOf("평상시", "음주 중")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, states)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userStateSpinner.adapter = adapter

        // 권한 확인 및 요청
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

        // DataClient 초기화
        dataClient = Wearable.getDataClient(this)
        skinTemperatureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // "데이터 전송" 버튼 클릭 시 UI 표시
        sendDataButton.setOnClickListener {
            dataInputLayout.visibility = View.VISIBLE
        }


        // 확인 버튼 클릭 시 전송 시작/중지
        confirmButton.setOnClickListener {
            if (!isSendingData) {
                // 🔹 전송 시작
                isSendingData = true
                confirmButton.text = "전송 중지"  // 🔹 버튼 텍스트 변경
                startDataSending()
            } else {
                // 🔹 전송 중지
                isSendingData = false
                confirmButton.text = "확인"  // 🔹 버튼 텍스트 변경
                handler.removeCallbacksAndMessages(null)  // 🔹 반복 실행 중단
            }
        }
    }
    private fun sendHeartRateData(heartRate: Float) {
        val putDataMapReq = PutDataMapRequest.create("/heart_rate").apply {
            dataMap.putInt("heart_rate", heartRate.toInt())
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest()
        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            Log.d("WearableApp", "심박수 데이터 전송 성공: $heartRate bpm")
        }.addOnFailureListener {
            Log.e("WearableApp", "심박수 데이터 전송 실패", it)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BODY_SENSORS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("Permission", "BODY_SENSORS permission granted.")
                startSensorMonitoring()
            } else {
                Log.e("Permission", "BODY_SENSORS permission denied.")
            }
        }
    }

    private fun startSensorMonitoring() {
        // 심박수 센서 등록
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i("Sensor", "Heart rate sensor started.")
        } ?: Log.e("Sensor", "Heart rate sensor not available.")
    }

    override fun onSensorChanged(event: SensorEvent?) {

        when (event?.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                val heartRate = event.values[0].toInt()
                sendDataToPhone()
                Log.d("WearOS", "심박수: $heartRate bpm")
                currentHeartRate = heartRate.toFloat()
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                val skinTemperature = event.values[0]
                sendDataToPhone()
                Log.d("WearOS", "피부 온도: $skinTemperature°C")
            }

            Sensor.TYPE_ACCELEROMETER -> handleMotion(event.values)
                // 기존 센서 처리 유지...

        }
    }

    private fun handleMotion(values: FloatArray) {
        detectTilt(values)
        detectShake(values)
    }

    private fun detectTilt(values: FloatArray) {
        val y = values[1]
        val z = values[2]
        val angle = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()

        if (abs(angle) > TILT_ANGLE && System.currentTimeMillis() - lastTiltTime > COOLDOWN_MS) {
            drinkCount++
            lastTiltTime = System.currentTimeMillis()
            updateDrinkUI()
            vibrate(200)
        }
    }


    private fun updateDrinkUI() {
        runOnUiThread {
            findViewById<TextView>(R.id.drinkCountTextView).text = "Drinks: $drinkCount"
        }
    }
    private fun vibrate(durationMs: Long) {
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun detectShake(values: FloatArray) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastShakeTime < COOLDOWN_MS) return

        val force = sqrt(
            values[0].pow(2) + values[1].pow(2) + values[2].pow(2)
        ).toFloat()

        if (force > SHAKE_THRESHOLD) {
            drinkCount = max(0, drinkCount - 1)
            lastShakeTime = currentTime
            updateDrinkUI()
            vibrate(500)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 센서 정확도 변경 처리 (필요 시)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 센서 리스너 해제
        sensorManager.unregisterListener(this)
    }

    // 🔹 1초마다 AWS로 데이터 전송하는 함수
    private fun startDataSending() {
        val sendDataRunnable = object : Runnable {
            override fun run() {
                if (isSendingData) {
                    sendDataToAWS()
                    handler.postDelayed(this, 1000)  // 🔹 1초 후 다시 실행
                }
            }
        }
        handler.post(sendDataRunnable)  // 🔹 첫 실행
    }
    private fun sendDataToPhone() {
        val putDataMapRequest = PutDataMapRequest.create("/drink_count").apply {
            dataMap.putInt("drink_count", drinkCount) // 🔹 키값 "drink_count"로 변경
            dataMap.putInt("heart_rate", currentHeartRate.toInt()) // 🔹 심박수 데이터 추가
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataRequest = putDataMapRequest.asPutDataRequest()
        val timestamp = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        dataClient.putDataItem(putDataRequest)
    }


    private fun sendDataToAWS() {
        val timestamp = Instant.now()
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val userState = userStateSpinner.selectedItem.toString()
        val alcoholPercentage = alcoholInput.text.toString().toFloatOrNull()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://moh7cm1z80.execute-api.us-east-1.amazonaws.com/prod/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiService::class.java)
        val sensorData = SensorData(timestamp, currentHeartRate.toInt(), drinkCount, userState, drinkCount, alcoholPercentage)

        api.sendSensorData(sensorData).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("API", "✅ 데이터 전송 성공")
                } else {
                    Log.e("API", "❌ 서버 응답 오류: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("API", "❌ 네트워크 오류: ${t.message}")
            }
        })
    }

}
