package com.example.smart_safety_management

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {

    const val BASE_URL = "https://xbjqxnvemcqubjfflain.supabase.co/functions/v1/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(SupabaseRoutingInterceptor())
            .addInterceptor(SupabaseAuthInterceptor())  // 2026-05-20: anon JWT header 강제
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val instance: SignUpService by lazy {
        retrofit.create(SignUpService::class.java)
    }

    val pttApi: PttApi by lazy {
        retrofit.create(PttApi::class.java)
    }
}

/**
 * 2026-05-20 — Supabase Edge Functions 가 verify_jwt=true 라 모든 호출에
 * `Authorization: Bearer <anon-key>` + `apikey: <anon-key>` 헤더 필요.
 * 401 "UNAUTHORIZED_NO_AUTH_HEADER" 방지.
 *
 * 호출자가 직접 헤더 지정한 경우 (예: Edge Function 안에서 service_role 호출 등) 는 보존.
 */
class SupabaseAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.header("Authorization") != null && req.header("apikey") != null) {
            return chain.proceed(req)
        }
        val builder = req.newBuilder()
        if (req.header("Authorization") == null) {
            builder.addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        }
        if (req.header("apikey") == null) {
            builder.addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
        }
        return chain.proceed(builder.build())
    }
}

/**
 * OkHttp Interceptor that routes old Express API paths to Supabase Edge Functions.
 *
 * - GET requests: query params are converted to a JSON POST body with an "action" field.
 * - POST (JSON) requests: the "action" field is injected into the existing JSON body.
 * - Multipart POST requests: image parts are uploaded to Supabase Storage via the
 *   "upload" Edge Function first, then text parts + image_urls are sent as JSON
 *   to the target Edge Function.
 */
class SupabaseRoutingInterceptor : Interceptor {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Routing tables ────────────────────────────────────────────────

    data class Route(val function: String, val action: String)

    private val getRoutes = mapOf(
        "/get_users" to Route("users", "get_users"),
        "/get_user_info" to Route("users", "get_user_info"),
        "/get_cctv_list" to Route("cameras", "list"),
        "/get_cctv_detail" to Route("cameras", "detail"),
        "/get_cctv_stream_info" to Route("cameras", "stream_info"),
        "/get_device_status" to Route("devices", "status"),
        "/get_worker_device_status" to Route("devices", "worker_status"),
        "/get_detection_events" to Route("detection", "events"),
        "/get_recent_detection_events" to Route("detection", "recent_events"),
        "/get_detection_event_detail" to Route("detection", "event_detail"),
        "/get_workplace" to Route("workplace", "get"),
        "/get_daily_checks" to Route("daily-checks", "list"),
        "/get_notifications" to Route("notifications", "list"),
        "/get_group_members" to Route("groups", "get_group_members"),
        "/get_event_types" to Route("detection", "event_types"),
        "/get_pending_invites" to Route("groups", "get_pending_invites"),
        "/get_workplace_location" to Route("workplace", "get_location"),
        "/get_camera_captures" to Route("cameras", "captures"),
        "/get_location" to Route("location", "get"),
        "/get_fire_detectors" to Route("devices", "fire_detectors"),
        "/get_arc_breakers" to Route("devices", "arc_breakers"),
        "/get_geofence_zones" to Route("location", "get_zones")
    )

    private val postRoutes = mapOf(
        "/signup" to Route("auth", "signup"),
        "/login" to Route("auth", "login"),
        "/find_id" to Route("auth", "find_id"),
        "/verify_user_for_password" to Route("auth", "verify_user_for_password"),
        "/change_password" to Route("auth", "change_password"),
        "/update_profile" to Route("users", "update_profile"),
        "/delete_account" to Route("users", "delete_account"),
        "/update_fcm_token" to Route("users", "update_fcm_token"),
        "/check_registered_contacts" to Route("users", "check_registered_contacts"),
        "/join_group" to Route("groups", "join_group"),
        "/remove_from_group" to Route("groups", "remove_from_group"),
        "/invite_members" to Route("groups", "invite_members"),
        "/cancel_invite" to Route("groups", "cancel_invite"),
        "/check_invite_availability" to Route("groups", "check_invite_availability"),
        "/create_workplace" to Route("workplace", "create"),
        "/delete_workplace" to Route("workplace", "delete"),
        "/update_workplace" to Route("workplace", "update"),
        "/reset_workplace_location" to Route("workplace", "reset_location"),
        "/register_workplace_location" to Route("workplace", "register_location"),
        "/delete_cameras" to Route("cameras", "delete"),
        "/register_camera" to Route("cameras", "register"), // 2026-05-21 Sprint A.2.3
        "/update_event_status" to Route("detection", "update_status"),
        "/handle_false_positive" to Route("detection", "handle_false_positive"),
        "/mark_notifications_read" to Route("notifications", "mark_read"),
        "/send_group_notification" to Route("notifications", "send_group"),
        "/send_individual_notification" to Route("notifications", "send_individual"),
        "/delete_daily_check" to Route("daily-checks", "delete"),
        "/update_worker_location" to Route("location", "update"),
        "/update_watch_status" to Route("devices", "update_watch"),
        "/create_geofence_zone" to Route("location", "create_zone"),
        "/delete_geofence_zone" to Route("location", "delete_zone"),
        "/update_geofence_zone" to Route("location", "update_zone"),
        "/send_verification_code" to Route("verification", "send_code"),
        "/check_verification_code" to Route("verification", "check_code"),
        "/create_mobile_fire_event" to Route("mobile-ai-event", "create_mobile_fire_event")
    )

    /** Multipart endpoints that need images uploaded to Storage first. */
    private val multipartRoutes = mapOf(
        "/create_action_request" to Route("actions", "create_request"),
        "/complete_action" to Route("actions", "complete"),
        "/create_daily_check" to Route("daily-checks", "create"),
        "/update_daily_check" to Route("daily-checks", "update"),
        "/complete_daily_check" to Route("daily-checks", "complete")
    )

    /** Maps multipart route paths to Storage bucket names. */
    private val bucketMap = mapOf(
        "/create_action_request" to "action-images",
        "/complete_action" to "action-images",
        "/create_daily_check" to "check-images",
        "/update_daily_check" to "check-images",
        "/complete_daily_check" to "check-images"
    )

    // ── Interceptor entry point ───────────────────────────────────────

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath.let { p ->
            // Strip the Edge Function base prefix so we get the old-style path
            // e.g. "/functions/v1/get_users" -> "/get_users"
            if (p.startsWith("/functions/v1")) p.removePrefix("/functions/v1") else p
        }

        // Upload endpoints stay as multipart (routed directly to the upload function)
        if (path == "/upload_image" || path == "/upload") {
            return chain.proceed(rewriteUpload(original))
        }

        // GET -> POST conversion
        getRoutes[path]?.let { route ->
            return chain.proceed(buildGetAsPost(original, route))
        }

        // Multipart POST -> upload images then JSON POST
        multipartRoutes[path]?.let { route ->
            val bucket = bucketMap[path] ?: "uploads"
            return chain.proceed(buildMultipartAsJson(original, route, bucket, chain))
        }

        // Regular POST -> inject action
        postRoutes[path]?.let { route ->
            return chain.proceed(buildPostWithAction(original, route))
        }

        // Not matched – pass through as-is (e.g. PttApi calls)
        return chain.proceed(original)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Rewrites /upload_image or /upload to POST to the "upload" Edge Function.
     * Keeps the body as multipart; adds ?bucket=profile-images if not set.
     */
    private fun rewriteUpload(original: Request): Request {
        val newUrl = original.url.newBuilder()
            .encodedPath("/functions/v1/upload")
            .apply {
                if (original.url.queryParameter("bucket") == null) {
                    addQueryParameter("bucket", "profile-images")
                }
            }
            .build()

        return original.newBuilder()
            .url(newUrl)
            .addHeader("Content-Type", original.body?.contentType()?.toString() ?: "multipart/form-data")
            .build()
    }

    /**
     * Converts a GET request with query params into a POST with JSON body.
     */
    private fun buildGetAsPost(original: Request, route: Route): Request {
        val json = JsonObject().apply {
            addProperty("action", route.action)
            // Copy all query params into the JSON body
            for (name in original.url.queryParameterNames) {
                val values = original.url.queryParameterValues(name)
                if (values.size == 1) {
                    val v = values[0] ?: continue
                    // Attempt to keep numeric types
                    v.toIntOrNull()?.let { addProperty(name, it) }
                        ?: v.toDoubleOrNull()?.let { addProperty(name, it) }
                        ?: addProperty(name, v)
                } else {
                    // Multiple values -> JSON array (e.g. events=fire&events=fall)
                    val arr = com.google.gson.JsonArray()
                    values.filterNotNull().forEach { arr.add(it) }
                    add(name, arr)
                }
            }
        }

        val newUrl = original.url.newBuilder()
            .encodedPath("/functions/v1/${route.function}")
            .query(null) // drop query params
            .build()

        return Request.Builder()
            .url(newUrl)
            .post(json.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .build()
    }

    /**
     * Injects the "action" field into an existing JSON POST body and reroutes
     * to the correct Edge Function path.
     */
    private fun buildPostWithAction(original: Request, route: Route): Request {
        val body = original.body
        val json: JsonObject = if (body != null && body.contentLength() > 0) {
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            val raw = buffer.readUtf8()
            try {
                JsonParser.parseString(raw).asJsonObject
            } catch (_: Exception) {
                JsonObject()
            }
        } else {
            JsonObject()
        }

        json.addProperty("action", route.action)

        val newUrl = original.url.newBuilder()
            .encodedPath("/functions/v1/${route.function}")
            .query(null)
            .build()

        return Request.Builder()
            .url(newUrl)
            .post(json.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .build()
    }

    /**
     * Handles multipart endpoints:
     * 1. Extracts text parts into a Map.
     * 2. Uploads each image part to Supabase Storage via the "upload" Edge Function.
     * 3. Builds a JSON body with text fields + image_urls array + action field.
     * 4. POSTs to the target Edge Function.
     */
    private fun buildMultipartAsJson(
        original: Request,
        route: Route,
        bucket: String,
        chain: Interceptor.Chain
    ): Request {
        val body = original.body
        val textFields = mutableMapOf<String, Any>()
        val imageUrls = mutableListOf<String>()
        val keptImageUrls = mutableListOf<String>()

        if (body is MultipartBody) {
            for (part in body.parts) {
                val disposition = part.headers?.get("Content-Disposition") ?: continue
                val nameMatch = Regex("""name="([^"]+)"""").find(disposition)
                val fieldName = nameMatch?.groupValues?.get(1) ?: continue
                val filenameMatch = Regex("""filename="([^"]+)"""").find(disposition)

                if (filenameMatch != null) {
                    // File part — upload to Storage
                    val filename = filenameMatch.groupValues[1]

                    val uploadUrl =
                        "${RetrofitClient.BASE_URL}upload?bucket=$bucket"

                    val uploadBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", filename, part.body)
                        .build()

                    val uploadRequest = Request.Builder()
                        .url(uploadUrl)
                        .post(uploadBody)
                        .build()

                    // D1: Execute upload synchronously, propagate failures.
                    //  기존에는 catch 블록에서 silently swallow 하여 DB 에 빈 이미지로
                    //  행이 생성되는 버그가 있었음. 이제 실패 시 IOException 을 throw
                    //  하여 Retrofit onFailure 에 도달하게 함.
                    val uploadResponse = chain.proceed(uploadRequest)
                    val uploadResponseBody = uploadResponse.body?.string()
                    uploadResponse.close()

                    if (!uploadResponse.isSuccessful) {
                        Log.e(
                            "RetrofitUpload",
                            "storage upload failed field=$fieldName bucket=$bucket " +
                                "code=${uploadResponse.code} body=$uploadResponseBody"
                        )
                        throw IOException(
                            "Storage 업로드 실패 (code=${uploadResponse.code}): $uploadResponseBody"
                        )
                    }
                    val url = try {
                        val respJson = JsonParser.parseString(uploadResponseBody).asJsonObject
                        respJson.get("imageUrl")?.asString ?: respJson.get("url")?.asString
                    } catch (e: Exception) {
                        Log.e(
                            "RetrofitUpload",
                            "upload response parse failed: $uploadResponseBody",
                            e
                        )
                        throw IOException("Storage 응답 파싱 실패", e)
                    }
                    if (url == null) {
                        Log.e(
                            "RetrofitUpload",
                            "upload response missing imageUrl: $uploadResponseBody"
                        )
                        throw IOException(
                            "Storage 응답에 imageUrl 필드 없음: $uploadResponseBody"
                        )
                    }
                    imageUrls.add(url)
                } else {
                    // Text part
                    val buf = okio.Buffer()
                    part.body.writeTo(buf)
                    val value = buf.readUtf8()

                    // B1: kept_image_urls 는 동일 이름으로 반복 전송되는 배열 의미.
                    //  기존 textFields Map 에 put 하면 마지막 값으로 덮어써져 1 개만
                    //  살아남는 데이터 손실 버그가 있었음. 전용 리스트에 append.
                    if (fieldName == "kept_image_urls") {
                        keptImageUrls.add(value)
                    } else {
                        // Try to parse as number for cleaner JSON
                        value.toIntOrNull()?.let { textFields[fieldName] = it }
                            ?: value.toDoubleOrNull()?.let { textFields[fieldName] = it }
                            ?: run { textFields[fieldName] = value }
                    }
                }
            }
        }

        // B2: update_daily_check 라우트만 kept_image_urls + new_image_urls 로 분리.
        //  그 외 경로(actions/create_request, actions/complete, daily-checks/create,
        //  daily-checks/complete)는 image_urls 단일 배열 (additive).
        val isDailyCheckUpdate =
            route.function == "daily-checks" && route.action == "update"

        val jsonObj = JsonObject().apply {
            addProperty("action", route.action)
            for ((key, value) in textFields) {
                when (value) {
                    is Int -> addProperty(key, value)
                    is Double -> addProperty(key, value)
                    is String -> addProperty(key, value)
                    else -> addProperty(key, value.toString())
                }
            }

            if (isDailyCheckUpdate) {
                if (keptImageUrls.isNotEmpty()) {
                    val arr = JsonArray().apply { keptImageUrls.forEach { add(it) } }
                    add("kept_image_urls", arr)
                }
                if (imageUrls.isNotEmpty()) {
                    val arr = JsonArray().apply { imageUrls.forEach { add(it) } }
                    add("new_image_urls", arr)
                }
            } else {
                if (imageUrls.isNotEmpty() || keptImageUrls.isNotEmpty()) {
                    val arr = JsonArray().apply {
                        keptImageUrls.forEach { add(it) }
                        imageUrls.forEach { add(it) }
                    }
                    add("image_urls", arr)
                }
            }
        }

        val newUrl = original.url.newBuilder()
            .encodedPath("/functions/v1/${route.function}")
            .query(null)
            .build()

        return Request.Builder()
            .url(newUrl)
            .post(jsonObj.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .build()
    }
}
