package com.example.todays_drink.todays_drink

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class PredictResponse(               // ← Lambda의 응답 형태
    val bac: Double,                      // 반드시 프로퍼티명 = JSON key
    val count: Int? = null
)
