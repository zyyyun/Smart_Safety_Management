package com.example.smart_safety_management

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {
    // ngrok 공용 주소로 변경하여 어디서든 접속 가능하도록 설정
    private const val BASE_URL = "https://uniterated-pardonless-laurena.ngrok-free.dev/"

    val instance: SignUpService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SignUpService::class.java)
    }

}