package com.example.smart_safety_management

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 안드로이드 에뮬레이터에서 로컬호스트(PC)에 접속하기 위한 주소 (10.0.2.2)
    private const val BASE_URL = "http://10.0.2.2:3000/"

    val instance: SignUpService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SignUpService::class.java)
    }
}