/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */
package com.example.waithealthdata.presentation

import android.Manifest
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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amplifyframework.api.rest.RestOptions
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.example.myapplication.R
import org.json.JSONObject
import com.amplifyframework.api.aws.AWSApiPlugin

class MainActivity : Activity(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private val interval = 30 * 1000L // 30초 간격

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var currentHeartRate: Float = -1f // 초기 심박수 값

    private val BODY_SENSORS_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Amplify 초기화
        try {
            Amplify.addPlugin(AWSApiPlugin()) // API 플러그인
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
            Log.i("MyAmplifyApp", "Initialized Amplify")
        } catch (error: Exception) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error)
        }

        // 센서 관리자 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor == null) {
            Log.e("Sensor", "Heart rate sensor is not available.")
            return
        }


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
            val dataCollector = DataCollector()
            dataCollector.startCollecting()

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
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = event.values[0] // 심박수 값 업데이트
            Log.i("Sensor", "Heart rate updated: $currentHeartRate")
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

    fun startDataCollection() {
        handler.post(object : Runnable {
            override fun run() {
                collectAndSendData()
                handler.postDelayed(this, interval)
            }
        })
    }

    private fun collectAndSendData() {
        if (currentHeartRate < 0) {
            Log.e("DataCollector", "No heart rate data available.")
            return
        }

        val timestamp = System.currentTimeMillis()
        val jsonData = JSONObject().apply {
            put("heartRate", currentHeartRate)
            put("timestamp", timestamp)
        }

        val requestOptions = RestOptions.builder()
            .addPath("/data")
            .addBody(jsonData.toString().toByteArray())
            .build()

        Amplify.API.post(
            requestOptions,
            { response -> Log.i("AWS", "Data sent: ${response.data.asString()}") },
            { error -> Log.e("AWS", "Failed to send data", error) }
        )

        Log.d("DataCollector", "Data collected and sent: $jsonData")
    }

    fun stopDataCollection() {
        handler.removeCallbacksAndMessages(null)
    }


}
