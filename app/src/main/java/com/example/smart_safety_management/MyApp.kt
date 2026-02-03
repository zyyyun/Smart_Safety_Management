package com.example.smart_safety_management

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import java.security.MessageDigest

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ 키 해시 로그 찍기 (디버깅용)
        printKeyHash()

        // ✅ 카카오맵 초기화
        KakaoMapSdk.init(this, "70b1fc4a6e71158e8bb19fd885f55113")
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
