package com.example.smart_safety_management

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class SignUpResponse(
    val message: String,
    val userName: String?
)

data class LoginRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    val message: String,
    val user: UserData?
)

data class UserData(
    @SerializedName("user_id") val userId: String,
    val name: String,
    @SerializedName("user_role") val userRole: String,
    @SerializedName("phone_num") val phoneNum: String?,
    val email: String?
)

data class UpdateProfileRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("phone_num") val phoneNum: String? = null,
    val email: String? = null,
    val name: String? = null
)

data class UpdateProfileResponse(
    val message: String
)

data class ChangePasswordRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("new_password") val newPassword: String
)

data class ChangePasswordResponse(
    val message: String
)

data class CreateWorkplaceRequest(
    @SerializedName("place_name") val placeName: String,
    @SerializedName("admin_id") val adminId: String
)

data class CreateWorkplaceResponse(
    val message: String
)

data class DeleteWorkplaceRequest(
    @SerializedName("place_name") val placeName: String,
    @SerializedName("admin_id") val adminId: String
)

data class DeleteWorkplaceResponse(
    val message: String
)

interface SignUpService {
    @POST("/signup")
    fun signUp(@Body request: SignUpRequest): Call<SignUpResponse>

    @POST("/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("/update_profile")
    fun updateProfile(@Body request: UpdateProfileRequest): Call<UpdateProfileResponse>

    @POST("/change_password")
    fun changePassword(@Body request: ChangePasswordRequest): Call<ChangePasswordResponse>

    @POST("/create_workplace")
    fun createWorkplace(@Body request: CreateWorkplaceRequest): Call<CreateWorkplaceResponse>

    @POST("/delete_workplace")
    fun deleteWorkplace(@Body request: DeleteWorkplaceRequest): Call<DeleteWorkplaceResponse>
}