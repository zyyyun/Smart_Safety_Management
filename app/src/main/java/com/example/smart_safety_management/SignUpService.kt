package com.example.smart_safety_management

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

data class SignUpRequest(
    @SerializedName("user_id") val userId: String,
    val password: String,
    val name: String,
    @SerializedName("phone_num") val phoneNum: String?,
    val email: String?,
    @SerializedName("user_role") val userRole: String,
    @SerializedName("group_id") val groupId: String?
)

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
    val email: String?,
    @SerializedName("profile_image_url") val profileImageUri: String?
)

data class UpdateProfileRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("phone_num") val phoneNum: String? = null,
    val email: String? = null,
    val name: String? = null,
    @SerializedName("profile_image_url") val profileImageUri: String? = null
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

data class CheckRegisteredContactsRequest(
    @SerializedName("phone_numbers") val phoneNumbers: List<String>
)

data class CheckRegisteredContactsResponse(
    @SerializedName("registered_phone_numbers") val registeredPhoneNumbers: List<String>
)

data class GetUsersResponse(
    val users: List<UserData>
)

data class RemoveFromGroupRequest(
    @SerializedName("user_id") val userId: String
)

data class RemoveFromGroupResponse(
    val message: String
)

data class CCTVItemResponse(
    @SerializedName("camera_id") val id: Int,
    @SerializedName("device_name") val name: String,
    @SerializedName("install_area") val location: String,
    @SerializedName("image_res_name") val imageResName: String?,
    val events: List<String>
)

data class GetCCTVListResponse(
    @SerializedName("cctv_list") val cctvList: List<CCTVItemResponse>
)

data class UploadImageResponse(
    val message: String,
    @SerializedName("imageUrl") val imageUrl: String
)

data class CCTVDetailResponse(
    @SerializedName("camera_id") val id: Int,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_code") val deviceCode: String?,
    @SerializedName("host_code") val hostCode: String?,
    @SerializedName("host_id") val hostId: String?,
    @SerializedName("host_password") val hostPassword: String?,
    @SerializedName("last_comm_date") val lastCommDate: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("install_area") val installArea: String?,
    @SerializedName("direction") val direction: String?,
    @SerializedName("shooting_interval") val shootingInterval: Int?,
    @SerializedName("operating_hours") val operatingHours: String?,
    val events: List<String>,
    @SerializedName("live_url") val liveUrl: String?,
    @SerializedName("installation_address") val installationAddress: String?
)

data class DeviceStatusDTO(
    val name: String,
    val role: String,
    @SerializedName("isGpsConnected") val isGpsConnected: Boolean,
    val battery: Int,
    @SerializedName("watchBattery") val watchBattery: Int
)

data class GetDeviceStatusResponse(
    @SerializedName("device_status") val deviceStatus: List<DeviceStatusDTO>
)

data class WorkerDeviceDTO(
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("battery_level") val batteryLevel: Int,
    @SerializedName("unworn_count") val unwornCount: Int?,
    @SerializedName("body_temp") val bodyTemp: Float?,
    @SerializedName("heart_rate") val heartRate: Int?
)

data class GetWorkerDeviceStatusResponse(
    @SerializedName("devices") val devices: List<WorkerDeviceDTO>
)

data class DetectionEventDTO(
    @SerializedName("event_id") val eventId: Int,
    @SerializedName("risk_level") val riskLevel: String?,
    @SerializedName("install_area") val installArea: String?,
    @SerializedName("event_name") val eventName: String?,
    @SerializedName("detected_at") val detectedAt: String,
    @SerializedName("device_name") val deviceName: String?,
    val accuracy: Double?,
    val status: String
)

data class GetDetectionEventsResponse(
    val events: List<DetectionEventDTO>
)

data class WorkplaceResponseItem(
    @SerializedName("name") val name: String
)

data class GetWorkplaceResponse(
    val workplaces: List<WorkplaceResponseItem>
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

    @POST("/check_registered_contacts")
    fun checkRegisteredContacts(@Body request: CheckRegisteredContactsRequest): Call<CheckRegisteredContactsResponse>

    @GET("/get_users")
    fun getUsers(@Query("user_id") userId: String): Call<GetUsersResponse>

    @POST("/remove_from_group")
    fun removeFromGroup(@Body request: RemoveFromGroupRequest): Call<RemoveFromGroupResponse>

    @GET("/get_cctv_list")
    fun getCCTVList(
        @Query("area") area: String?,
        @Query("event_names") eventNames: List<String>?,
        @Query("user_id") userId: String
    ): Call<GetCCTVListResponse>

    @Multipart
    @POST("/upload")
    fun uploadImage(@Part image: MultipartBody.Part): Call<UploadImageResponse>

    @GET("/get_cctv_detail")
    fun getCCTVDetail(@Query("camera_id") cameraId: String): Call<CCTVDetailResponse>

    @GET("/get_device_status")
    fun getDeviceStatus(@Query("user_id") userId: String): Call<GetDeviceStatusResponse>

    @GET("/get_worker_device_status")
    fun getWorkerDeviceStatus(@Query("user_id") userId: String): Call<GetWorkerDeviceStatusResponse>

    @GET("/get_detection_events")
    fun getDetectionEvents(@Query("user_id") userId: String): Call<GetDetectionEventsResponse>

    @GET("/get_workplace")
    fun getWorkplace(@Query("user_id") userId: String): Call<GetWorkplaceResponse>
}