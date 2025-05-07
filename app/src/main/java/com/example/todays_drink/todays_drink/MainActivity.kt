package com.example.todays_drink.todays_drink

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.todays_drink.R
import com.google.android.gms.wearable.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

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

class MainActivity : Activity(), SensorEventListener, DataClient.OnDataChangedListener {

    private var isSendingData = false
    private val handler = Handler(Looper.getMainLooper())
    private val interval = 2 * 1000L

    private lateinit var dataClient: DataClient
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var skinTemperatureSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var currentHeartRate: Float = -1f // 초기 심박수 값
    private val TILT_ANGLE = 60f // 기울기 감지 각도
    private val SHAKE_THRESHOLD = 15f // 흔들림 감지 임계값
    private val COOLDOWN_MS = 10000L // 중복 실행 방지 시간

    private lateinit var sendDataButton: Button
    private lateinit var userStateSpinner: Spinner
    private lateinit var dataInputLayout: LinearLayout
    private lateinit var sensorSettingView: View
    private lateinit var resetSensorButton: Button
    private lateinit var saveSensorButton: Button
    private lateinit var mainWrapper: LinearLayout
    private lateinit var drinkCountTextView2: TextView
    private lateinit var toggleViewButton: Button
    private lateinit var layoutDisableButton: Button
    private lateinit var layoutDisableButton2: Button
    // ――― 시간 관리용 ―――
    private var initTime: Long = 0L            // 앱 시작(또는 전송 시작) 시간
    private val baseTimeStack = ArrayDeque<Long>() // 마실 때마다 쌓는 스택
    private var baseTime: Long = 0L            // 현재 경과 기준 시간

    // 센서 처리 부분
    private var baselineGravity: FloatArray? = null
    private var baselineGyro: FloatArray? = null
    private var savedGravity: FloatArray? = null
    private var savedGyro: FloatArray? = null
    private var currentGravity: FloatArray? = null
    private var currentGyro: FloatArray? = null
    private val ALPHA = 0.1f  // 필터 강도 조절 (0에 가까울수록 반응이 느려짐)
    private var tiltStartTime = -1L                      // 기울기 시작 시간
    private val TILT_HOLD_THRESHOLD = 800L              // 몇 ms 이상 유지되어야 마심으로 감지할지
    private val DRINK_MATCH_ANGLE = 20.0  // 20도 이하이면 "비슷한 자세"라고 본다
    private var lastDrinkTime = 0L               // 마지막으로 감지된 시점

    private fun lowPassFilter(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] + ALPHA * (input[i] - output[i])
        }
        return output
    }
    private var drinkCount = 0
    private var lastTiltTime = 0L
    private var lastShakeTime = 0L
    private var alcoholInput = 0L


    private val BODY_SENSORS_PERMISSION_REQUEST_CODE = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
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

        // DataClient 초기화 & 리스너 등록
        dataClient = Wearable.getDataClient(this)
        dataClient.addListener(this)
        // UI 초기화
        sendDataButton = findViewById(R.id.sendDataButton)
        // userStateSpinner = findViewById(R.id.userStateSpinner) 도수 체크용 스피너, 사용하지 않음
        dataInputLayout = findViewById(R.id.dataInputLayout)
        resetSensorButton = findViewById(R.id.resetSensorButton)
        saveSensorButton = findViewById(R.id.saveSensorButton)
        sensorSettingView = findViewById(R.id.sensorSettingView)
        mainWrapper = findViewById(R.id.mainWrapper)
        drinkCountTextView2 = findViewById(R.id.drinkCountTextView2)
        toggleViewButton = findViewById(R.id.toggleViewButton)
        layoutDisableButton = findViewById(R.id.layoutDisableButton)
        layoutDisableButton2 = findViewById(R.id.layoutDisableButton2)

        dataInputLayout.visibility = View.GONE
        sensorSettingView.visibility = View.GONE

        /*
        // Spinner 설정 (음주X / 음주중)
        val states = listOf("선택", "소주", "맥주")

        val adapter = ArrayAdapter(this, R.layout.spinner_item, R.id.spinnerText, states)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        userStateSpinner.adapter = adapter
        userStateSpinner.setSelection(0)
        userStateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                alcoholInput = when (pos) {          // pos: 0-선택, 1-소주, 2-맥주
                    1 -> 17L                          // 소주 → 17%
                    2 -> 5L                           // 맥주 → 5%
                    else -> 0L                        // 선택 안 함
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) { /* no-op */ }
        }

         */


        sendDataButton.setOnClickListener {
            // 메인 화면 요소 숨기고 측정 화면 표시
            mainWrapper.visibility = View.INVISIBLE
            dataInputLayout.visibility = View.VISIBLE
            drinkCountTextView2.text = "Drinks: $drinkCount"

            // 전송 시작

            isSendingData = true

            initTime = System.currentTimeMillis()
            baseTimeStack.clear()
            baseTime = initTime         // 첫 기준 시간

            startDataSending()

        }

        toggleViewButton.setOnClickListener {
            sensorSettingView.visibility = View.VISIBLE
        }

        resetSensorButton.setOnClickListener {
            resetSensorValues()
            Toast.makeText(this, "센서 초기화됨", Toast.LENGTH_SHORT).show()
        }

        saveSensorButton.setOnClickListener {
            saveSensorValues()
            Toast.makeText(this, "센서값 저장됨", Toast.LENGTH_SHORT).show()
        }

        layoutDisableButton.setOnClickListener{
            sensorSettingView.visibility = View.INVISIBLE
        }

        layoutDisableButton2.setOnClickListener{
            dataInputLayout.visibility = View.INVISIBLE
            mainWrapper.visibility = View.VISIBLE
            // 🔹 전송 중지
            isSendingData = false
            handler.removeCallbacksAndMessages(null)  // 🔹 반복 실행 중단

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
        skinTemperatureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
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


    fun detectShake(event: SensorEvent) {
        if (baselineGravity == null) return  // 기준값이 없으면 감지 안함


        val x = event.values[0] - baselineGravity!![0]
        val y = event.values[1] - baselineGravity!![1]
        val z = event.values[2] - baselineGravity!![2]

        val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (acceleration > SHAKE_THRESHOLD) {
            drinkCount = max(0, drinkCount - 1)
            if (baseTimeStack.isNotEmpty()) baseTimeStack.pop()
            baseTime = if (baseTimeStack.isNotEmpty()) baseTimeStack.peek() else initTime
            vibrateWatch()
            Log.d("ShakeDetection", "Shake detected! Count: $drinkCount")
            updateDrinkUI()
        }
    }

    private fun vibrateWatch() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }
    }


    private fun detectDrinkingWithSavedGravity() {
        if (savedGravity == null || currentGravity == null) return

        val now = System.currentTimeMillis()

        // 1) 만약 마지막 검출로부터 5초가 지나지 않았다면, 그대로 리턴
        if (now - lastDrinkTime < COOLDOWN_MS) {
            return
        }

        // 2) 현재 자세와 저장된 "마시는 자세" 사이 각도를 구해서,
        //    충분히 비슷하면(20도 이하) "팔을 들어올린 상태"라고 간주
        val angle = angleBetween(savedGravity!!, currentGravity!!)

        if (angle < DRINK_MATCH_ANGLE) {
            // 기울임 시작점이 -1L이면 지금을 기울임 시작으로 기록
            if (tiltStartTime < 0) {
                tiltStartTime = now
            } else {
                // 이미 기울기 중이라면, 얼마나 유지됐는지 확인
                if (now - tiltStartTime >= TILT_HOLD_THRESHOLD) {
                    // (마시는 동작 감지)
                    drinkCount++
                    val now = System.currentTimeMillis()
                    baseTimeStack.push(now)   // 새 기준 시간을 쌓음
                    baseTime = now

                    vibrateWatch()
                    updateDrinkUI()

                    Log.d("DrinkingDetection", "Drink detected! Count=$drinkCount")

                    // 감지 시점 기록 + 기울임 상태 초기화
                    lastDrinkTime = now
                    tiltStartTime = -1L
                }
            }
        } else {
            // "마시는 자세" 범위에서 벗어났으므로 기울임 상태 리셋
            tiltStartTime = -1L
        }
    }


    private fun angleBetween(g1: FloatArray, g2: FloatArray): Double {
        val dot = g1[0]*g2[0] + g1[1]*g2[1] + g1[2]*g2[2]
        val mag1 = sqrt(g1[0]*g1[0] + g1[1]*g1[1] + g1[2]*g1[2])
        val mag2 = sqrt(g2[0]*g2[0] + g2[1]*g2[1] + g2[2]*g2[2])
        val cosine = dot / (mag1 * mag2)
        val clamped = cosine.coerceIn((-1.0).toFloat(), 1.0F)
        val angleRadians = acos(clamped)
        return Math.toDegrees(angleRadians.toDouble())
    }
    fun saveSensorValues() {
        savedGravity = currentGravity?.clone()
        savedGyro = currentGyro?.clone()
        Log.d("SensorSave", "센서값 저장됨: Gravity=${savedGravity?.contentToString()}, Gyro=${savedGyro?.contentToString()}")
    }

    fun resetSensorValues() {
        baselineGravity = currentGravity?.clone()
        baselineGyro = currentGyro?.clone()
        Log.d("SensorReset", "센서 초기값 설정됨: Gravity=${baselineGravity?.contentToString()}, Gyro=${baselineGyro?.contentToString()}")
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
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-Pass Filter 적용 (작은 움직임 필터링)
                currentGravity = lowPassFilter(event.values.clone(), currentGravity)

                // 마시는 행동 감지
                detectDrinkingWithSavedGravity()

                // 흔들림 감지
                detectShake(event)
            }

            Sensor.TYPE_GYROSCOPE -> {
                // 자이로 데이터 저장
                currentGyro = lowPassFilter(event.values.clone(), currentGyro)
            }
        }
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

    override fun onDataChanged(buffer: DataEventBuffer) {
        for (ev in buffer) {
            if (ev.type == DataEvent.TYPE_CHANGED &&
                ev.dataItem.uri.path?.startsWith("/drink_info") == true) {

                val map = DataMapItem.fromDataItem(ev.dataItem).dataMap
                val percent = map.getFloat("alcohol_percentage")
                alcoholInput = percent.toLong()      // 기존 변수 사용

            }
        }
    }

    // 🔹 60초마다 AWS로 데이터 전송하는 함수
    private fun startDataSending() {
        val sendDataRunnable = object : Runnable {
            override fun run() {
                if (isSendingData) {
                    sendDataToAWS()
                    handler.postDelayed(this, interval)  // 🔹 60초 후 다시 실행
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
        val now = System.currentTimeMillis()
        val elapsed = (now - baseTime) / 1000 / 60
        val userState = if (isSendingData) "음주 중" else "평상시"
        val alcoholPercentage = alcoholInput.toFloat()

        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val ok = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()
        val retrofit = Retrofit.Builder()
            .client(ok)
            .baseUrl("https://moh7cm1z80.execute-api.us-east-1.amazonaws.com/prod/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiService::class.java)
        val sensorData = SensorData(timestamp, currentHeartRate.toInt(), drinkCount, elapsed, userState, drinkCount, alcoholPercentage)

        api.sendSensorData(sensorData).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("API", "✅ 데이터 전송 성공, ${elapsed}")
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
