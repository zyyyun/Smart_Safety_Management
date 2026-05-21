package com.example.smart_safety_management

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class MyApp : Application() {

    // Phase 7 / D-01 — SupabaseClient 싱글톤 (process 단일 인스턴스, WSS 재연결 비용 절약).
    // anon key 만 사용. service_role 은 절대 client 에 두지 않음 — Edge Function (07-02) 책임.
    // ktor-client-cio engine 사용 — Retrofit OkHttp 4.12.0 와 transitive 충돌 회피 (Pitfall 7).
    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Realtime)
            install(Postgrest)
            install(Storage)  // Phase 9 / 09-03 TBM-02 — 수기 서명 PNG 업로드용
            httpEngine = CIO.create()
            // 2026-05-19 fix: 응답 JSON 의 unknown 필드 무시. 002_tables.sql 의 devices
            // 가 5 추가 컬럼 (serial_number/battery_level/gps_status/updated_at/firmware_version)
            // 을 anon 응답에 포함 → strict mode 면 SerializationException → fetch null →
            // PairWatchSection 재진입 시 paired 상태 손실. lenient mode 로 모든 decode 안전.
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true  // null → default value coercion (e.g., Boolean? null → false default)
            })
        }
    }

    override fun onCreate() {
        super.onCreate()

        val kakaoNativeAppKey = BuildConfig.KAKAO_NATIVE_APP_KEY
        if (kakaoNativeAppKey.isBlank() || kakaoNativeAppKey.startsWith("SAMPLE")) {
            Log.e("KakaoMap", "Kakao native app key is missing. Set kakao.nativeAppKey in local.properties.")
        } else {
            KakaoMapSdk.init(this, kakaoNativeAppKey)
        }

        // 키 해시 로그 찍기
        printKeyHash()

        // 발급받은 카카오맵 네이티브 앱 키
        KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")

        // 2026-05-19 fix: SupabaseClient by-lazy 사전 warm-up.
        // 첫 진입 (SettingDeviceManagement / TBM 카드) 시 lazy block 의 CIO+Realtime+
        // Postgrest+Storage install 비용이 main thread 에 부하 → cold-start 예외 또는
        // ANR → Activity 강제 종료 ("첫 진입 튕김"). 백그라운드 thread 에서 미리 인스턴스
        // 생성하면 사용자가 화면 진입할 시점엔 이미 ready 상태.
        // 단, WSS 연결은 첫 channel.subscribe() 시점에 시작 — 트래픽 비용 zero.
        Thread {
            runCatching { supabase }
                .onFailure { android.util.Log.w("MyApp", "Supabase warm-up failed: ${it.message}", it) }
        }.start()
    }

    private fun printKeyHash() {
        try {
            val packageName = packageName
            val pm = packageManager

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }

            signatures.forEach { sig ->
                val md = MessageDigest.getInstance("SHA")
                md.update(sig.toByteArray())
                val keyHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                Log.e("KEY_HASH", keyHash)
            }
        } catch (e: Exception) {
            Log.e("KEY_HASH", "printKeyHash error: ${e.message}", e)
        }
    }
}
