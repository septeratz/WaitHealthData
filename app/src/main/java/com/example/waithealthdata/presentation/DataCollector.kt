package com.example.waithealthdata.presentation

import android.os.Handler
import android.os.Looper
import com.amplifyframework.api.rest.RestOptions
import com.amplifyframework.core.Amplify
import android.util.Log

class DataCollector {
    private val handler = Handler(Looper.getMainLooper())
    private val interval = 30 * 1000L // 30초 간격
    private var heartRate: Int = 0 // 심박수 데이터

    fun startCollecting() {
        handler.post(object : Runnable {
            override fun run() {
                collectHeartRate()
                sendDataToAWS(heartRate)
                handler.postDelayed(this, interval)
            }
        })
    }

    private fun collectHeartRate() {
        // 심박수 데이터 수집 로직 (예: SensorEventListener)
        heartRate = (60..100).random() // 임시 랜덤 데이터
        Log.d("DataCollector", "Collected heart rate: $heartRate")
    }

    private fun sendDataToAWS(heartRate: Int) {
        val requestBody = """
            {
                "heartRate": $heartRate,
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        val options = RestOptions.builder()
            .addPath("/resource")
            .addHeader("x-api-key", "Qb0DjUSSNi8Pl7rkM7YQwa1upwBqv92w1LpiPTII") // API Key 추가
            .addBody(requestBody.toByteArray())
            .build()

        Amplify.API.post(
            options,
            { response -> Log.i("AmplifyAPI", "Data sent successfully: ${response.data.asString()}") },
            { error -> Log.e("AmplifyAPI", "Failed to send data", error) }
        )

    }

    fun stopCollecting() {
        handler.removeCallbacksAndMessages(null)
    }
}
