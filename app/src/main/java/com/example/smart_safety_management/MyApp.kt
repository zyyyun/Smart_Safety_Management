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
import io.ktor.client.engine.cio.CIO
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
            httpEngine = CIO.create()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 키 해시 로그 찍기
        printKeyHash()

        // 발급받은 카카오맵 네이티브 앱 키
        KakaoMapSdk.init(this, "SAMPLE_NATIVE_APP_KEY")

        // supabase 는 by lazy — 첫 사용 (Realtime 구독 / PostgREST select) 시점에 WSS 연결.
        // 앱 시작 시 미리 connect 하지 않음 (모든 사용자가 워치 사용자가 아니므로 zero-cost 시작).
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
