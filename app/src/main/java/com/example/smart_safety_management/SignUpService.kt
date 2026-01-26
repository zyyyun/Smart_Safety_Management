package com.example.smart_safety_management

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class SignUpResponse(
    val message: String,
    val userName: String?
)

interface SignUpService {
    @POST("/signup")
    fun signUp(@Body request: SignUpRequest): Call<SignUpResponse>
}