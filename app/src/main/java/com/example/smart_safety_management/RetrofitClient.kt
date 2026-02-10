package com.example.smart_safety_management

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // ngrok 공용 주소
    const val BASE_URL = "https://uniterated-pardonless-laurena.ngrok-free.dev/"

    // ✅ Retrofit 인스턴스를 1번만 생성해서 재사용
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ✅ 기존 API
    val instance: SignUpService by lazy {
        retrofit.create(SignUpService::class.java)
    }

    // ✅ 새로 추가: 음성 업로드 API
    val pttApi: PttApi by lazy {
        retrofit.create(PttApi::class.java)
    }
}
