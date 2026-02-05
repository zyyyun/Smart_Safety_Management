package com.example.smart_safety_management

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

data class JoinGroupRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("invite_code") val inviteCode: String
)

data class JoinGroupResponse(
    val message: String,
    @SerializedName("group_id") val groupId: String
)


data class VerificationRequest(
    @SerializedName("phone_num") val phoneNum: String,
    @SerializedName("app_hash") val appHash: String // 앱 해시 추가
)

data class VerificationResponse(
    val message: String
)

data class CheckVerificationRequest(
    @SerializedName("phone_num") val phoneNum: String,
    @SerializedName("verification_code") val verificationCode: String
)

data class CheckVerificationResponse(
    val message: String,
    val isVerified: Boolean
)

data class SignUpRequest(
    @SerializedName("user_id") val userId: String,
    val password: String,
    val name: String,
    @SerializedName("phone_num") val phoneNum: String?,
    val email: String?,
    @SerializedName("user_role") val userRole: String,
    @SerializedName("group_id") val groupId: String?,
    @SerializedName("invite_code") val inviteCode: String? = null
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
    @SerializedName("user_id") val userId: String?,
    val name: String,
    @SerializedName("user_role") val userRole: String,
    @SerializedName("phone_num") val phoneNum: String?,
    val email: String?,
    @SerializedName("profile_image_url") val profileImageUri: String?,
    @SerializedName("group_id") val groupId: String?,
    @SerializedName("invite_code") val inviteCode: String?, // 초대코드 추가
    @SerializedName("is_invite_checked") val isInviteChecked: Boolean = false // 초대코드 확인 여부 추가
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
    val events: List<String>,
    @SerializedName("environment_type") val environmentType: String?,
    @SerializedName("installation_address") val installationAddress: String?,
    @SerializedName("live_url") val liveUrl: String?,
    @SerializedName("live_url_detail") val liveUrlDetail: String?
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
    @SerializedName("installation_address") val installationAddress: String?,
    @SerializedName("live_url_detail") val liveUrlDetail: String?
)

data class CCTVStreamInfoResponse(
    @SerializedName("camera_id") val id: Int,
    @SerializedName("live_url") val liveUrl: String?,
    @SerializedName("live_url_detail") val liveUrlDetail: String?,
    @SerializedName("install_area") val installArea: String?,
    @SerializedName("installation_address") val installationAddress: String?
)

data class DeviceStatusDTO(
    @SerializedName("user_id") val userId: String, // 추가
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
    val status: String,
    @SerializedName("worker_name") val workerName: String?,
    @SerializedName("completed_at") val actionTime: String?
)

data class GetDetectionEventsResponse(
    val events: List<DetectionEventDTO>
)

data class DetectionEventDetailResponse(
    @SerializedName("event_id") val eventId: Int,
    @SerializedName("risk_level") val riskLevel: String?,
    @SerializedName("install_area") val installArea: String?,
    @SerializedName("event_name") val eventName: String?,
    @SerializedName("detected_at") val detectedAt: String?,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("accuracy") val accuracy: Double?,
    @SerializedName("status") val status: String?,
    @SerializedName("installation_address") val installationAddress: String?,
    @SerializedName("live_url") val liveUrl: String?,
    @SerializedName("capture_image_url") val captureImageUrl: String?,
    @SerializedName("request_type") val requestType: String?,
    @SerializedName("request_title") val requestTitle: String?,
    @SerializedName("request_details") val requestDetails: String?,
    @SerializedName("action_images") val actionImages: List<String>?
)

data class WorkplaceResponseItem(
    @SerializedName("name") val name: String
)

data class GetWorkplaceResponse(
    val workplaces: List<WorkplaceResponseItem>
)

data class DeleteAccountRequest(
    @SerializedName("user_id") val userId: String
)

data class DeleteAccountResponse(
    val message: String
)

data class UpdateEventStatusRequest(
    @SerializedName("event_id") val eventId: Int,
    @SerializedName("status") val status: String
)

data class CompleteActionRequest(
    @SerializedName("event_id") val eventId: Int,
    @SerializedName("worker_id") val workerId: String
)

data class HandleFalsePositiveRequest(
    @SerializedName("event_id") val eventId: Int,
    @SerializedName("user_id") val userId: String
)

data class FindIdRequest(
    val name: String,
    @SerializedName("phone_num") val phoneNum: String
)

data class FindIdRequestFindIdResponse(
    val message: String,
    @SerializedName("user_id") val userId: String?
)

data class FindIdResponse(
    val message: String,
    @SerializedName("user_id") val userId: String?
)

data class VerifyUserRequest(
    val name: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("phone_num") val phoneNum: String
)

data class VerifyUserResponse(
    val message: String,
    val exists: Boolean
)

data class DailyCheckDTO(
    @SerializedName("check_id") val checkId: Int,
    val location: String,
    val hazard: String?,
    val countermeasure: String?,
    val status: String,
    @SerializedName("check_date") val checkDate: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("writer_id") val writerId: String,
    @SerializedName("worker_id") val workerId: String?,
    val images: List<String>?
)

data class CreateDailyCheckResponse(
    val message: String,
    @SerializedName("check_id") val checkId: Int,
    @SerializedName("image_urls") val imageUrls: List<String>
)

data class GetDailyChecksResponse(
    val checks: List<DailyCheckDTO>
)

data class NotificationDTO(
    @SerializedName("notification_id") val id: Int,
    val title: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_read") var isRead: Boolean
)

data class GetNotificationsResponse(
    val notifications: List<NotificationDTO>
)

data class MarkNotificationReadRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("notification_id") val notificationId: Int? = null
)

data class DeleteDailyCheckRequest(
    @SerializedName("check_id") val checkId: Int
)

data class DeleteDailyCheckResponse(
    val message: String
)

data class GroupMembersResponse(val members: List<String>)

data class EventTypesResponse(val event_types: List<String>)

data class SendGroupNotificationRequest(
    @SerializedName("sender_id") val senderId: String,
    val title: String,
    val content: String
)

data class SendIndividualNotificationRequest(
    @SerializedName("user_id") val userId: String,
    val title: String,
    val content: String
)

data class DeleteCamerasRequest(
    @SerializedName("camera_ids") val cameraIds: List<Int>
)

data class DeleteCamerasResponse(
    val message: String
)

data class GetUserInfoResponse(
    @SerializedName("user_id") val userId: String,
    val name: String,
    @SerializedName("phone_num") val phoneNum: String?,
    val email: String?,
    @SerializedName("user_role") val userRole: String,
    @SerializedName("group_id") val groupId: Int?,
    @SerializedName("is_invite_checked") val isInviteChecked: Boolean,
    @SerializedName("invite_code") val inviteCode: String?,
    @SerializedName("profile_image_url") val profileImage: String?
)

data class CheckInviteAvailabilityRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("phone_numbers") val phoneNumbers: List<String>
)

data class CheckInviteAvailabilityResponse(
    @SerializedName("available_phone_numbers") val availablePhoneNumbers: List<String>
)

data class InviteContactDTO(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("name") val name: String
)

data class InviteMembersRequest(
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("contacts") val contacts: List<InviteContactDTO>,
    @SerializedName("role") val role: String
)

data class InviteMembersResponse(
    val message: String
)

data class PendingInviteDTO(
    @SerializedName("phone_number") val phoneNumber: String,
    val name: String,
    @SerializedName("user_role") val userRole: String?
)

data class GetPendingInvitesResponse(
    @SerializedName("pending_invites") val pendingInvites: List<PendingInviteDTO>
)

data class CancelInviteRequest(
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("phone_numbers") val phoneNumbers: List<String>? = null
)

data class CancelInviteResponse(
    val message: String
)

data class WorkplaceLocationResponse(
    val address: String?,
    @SerializedName("road_address") val roadAddress: String?
)

interface SignUpService {
    @POST("/send_verification_code")
    fun sendVerificationCode(@Body request: VerificationRequest): Call<VerificationResponse>

    @POST("/check_verification_code")
    fun checkVerificationCode(@Body request: CheckVerificationRequest): Call<CheckVerificationResponse>

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
        @Query("events") eventNames: List<String>?, // ✅ [수정] 서버 변수명(events)과 일치시킴
        @Query("user_id") userId: String
    ): Call<GetCCTVListResponse>

    @Multipart
    @POST("/upload_image")
    fun uploadImage(@Part image: MultipartBody.Part): Call<UploadImageResponse>

    @GET("/get_cctv_detail")
    fun getCCTVDetail(@Query("camera_id") cameraId: String): Call<CCTVDetailResponse>

    @GET("/get_cctv_stream_info")
    fun getCCTVStreamInfo(@Query("camera_id") cameraId: String): Call<CCTVStreamInfoResponse>

    @GET("/get_device_status")
    fun getDeviceStatus(@Query("user_id") userId: String): Call<GetDeviceStatusResponse>

    @GET("/get_worker_device_status")
    fun getWorkerDeviceStatus(@Query("user_id") userId: String): Call<GetWorkerDeviceStatusResponse>

    @GET("/get_detection_events")
    fun getDetectionEvents(@Query("user_id") userId: String): Call<GetDetectionEventsResponse>

    @GET("/get_detection_event_detail")
    fun getDetectionEventDetail(@Query("event_id") eventId: Int): Call<DetectionEventDetailResponse>

    @GET("/get_workplace")
    fun getWorkplace(@Query("user_id") userId: String): Call<GetWorkplaceResponse>

    @POST("/delete_account")
    fun deleteAccount(@Body request: DeleteAccountRequest): Call<Void>

    @Multipart
    @POST("/create_action_request")
    fun createActionRequest(
        @Part("event_id") eventId: RequestBody,
        @Part("requester_id") requesterId: RequestBody,
        @Part("request_type") requestType: RequestBody,
        @Part("request_title") requestTitle: RequestBody,
        @Part("request_details") requestDetails: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Call<Void>

    @POST("/update_event_status")
    fun updateEventStatus(@Body request: UpdateEventStatusRequest): Call<Void>

    @POST("/complete_action")
    fun completeAction(@Body request: CompleteActionRequest): Call<Void>

    @POST("/handle_false_positive")
    fun handleFalsePositive(@Body request: HandleFalsePositiveRequest): Call<Void>

    @POST("/find_id")
    fun findId(@Body request: FindIdRequest): Call<FindIdResponse>

    @POST("/verify_user_for_password")
    fun verifyUserForPassword(@Body request: VerifyUserRequest): Call<VerifyUserResponse>

    @GET("/get_daily_checks")
    fun getDailyChecks(
        @Query("user_id") userId: String,
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Call<GetDailyChecksResponse>

    @Multipart
    @POST("/create_daily_check")
    fun createDailyCheck(
        @Part("writer_id") writerId: RequestBody,
        @Part("location") location: RequestBody,
        @Part("hazard") hazard: RequestBody,
        @Part("countermeasure") countermeasure: RequestBody,
        @Part("check_date") checkDate: RequestBody?,
        @Part images: List<MultipartBody.Part>
    ): Call<CreateDailyCheckResponse>

    @Multipart
    @POST("/update_daily_check")
    fun updateDailyCheck(
        @Part("check_id") checkId: RequestBody,
        @Part("location") location: RequestBody,
        @Part("hazard") hazard: RequestBody,
        @Part("countermeasure") countermeasure: RequestBody,
        @Part("check_date") checkDate: RequestBody?,
        @Part keptImageUrls: List<MultipartBody.Part>,
        @Part newImages: List<MultipartBody.Part>
    ): Call<CreateDailyCheckResponse>

    @GET("/get_notifications")
    fun getNotifications(@Query("user_id") userId: String): Call<GetNotificationsResponse>

    @POST("/mark_notifications_read")
    fun markNotificationsRead(@Body request: MarkNotificationReadRequest): Call<Void>

    @POST("/delete_daily_check")
    fun deleteDailyCheck(@Body request: DeleteDailyCheckRequest): Call<DeleteDailyCheckResponse>

    @POST("/join_group")
    fun joinGroup(@Body request: JoinGroupRequest): Call<JoinGroupResponse>

    @Multipart
    @POST("/complete_daily_check")
    fun completeDailyCheck(
        @Part("check_id") checkId: RequestBody,
        @Part("worker_id") workerId: RequestBody,
        @Part("check_content") checkContent: RequestBody,
        @Part("check_date") checkDate: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Call<Void>

    @GET("/get_group_members")
    fun getGroupMembers(@Query("user_id") userId: String): Call<GroupMembersResponse>

    @GET("/get_event_types")
    fun getEventTypes(): Call<EventTypesResponse>

    @POST("/send_group_notification")
    fun sendGroupNotification(@Body request: SendGroupNotificationRequest): Call<Void>

    @POST("/send_individual_notification")
    fun sendIndividualNotification(@Body request: SendIndividualNotificationRequest): Call<Void>

    @POST("/delete_cameras")
    fun deleteCameras(@Body request: DeleteCamerasRequest): Call<DeleteCamerasResponse>

    @GET("/get_user_info")
    fun getUserInfo(@Query("user_id") userId: String): Call<GetUserInfoResponse>

    @POST("/check_invite_availability")
    fun checkInviteAvailability(@Body request: CheckInviteAvailabilityRequest): Call<CheckInviteAvailabilityResponse>

    @POST("/invite_members")
    fun inviteMembers(@Body request: InviteMembersRequest): Call<InviteMembersResponse>

    @GET("/get_pending_invites")
    fun getPendingInvites(@Query("user_id") userId: String): Call<GetPendingInvitesResponse>

    @POST("/cancel_invite")
    fun cancelInvite(@Body request: CancelInviteRequest): Call<CancelInviteResponse>

    @GET("/get_workplace_location")
    fun getWorkplaceLocation(@Query("user_id") userId: String): Call<WorkplaceLocationResponse>
}
