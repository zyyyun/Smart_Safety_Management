package com.example.smart_safety_management.watch

import android.content.Context
import com.example.smart_safety_management.MyApp
import io.github.jan.supabase.SupabaseClient

/**
 * Phase 7 / 07-03 Wave 3 — SupabaseClient 싱글톤 accessor.
 * Application 의 by-lazy 인스턴스를 어디서든 얻기 위한 helper.
 * 호출 예: `val client = SupabaseModule.client(LocalContext.current)`
 */
object SupabaseModule {
    fun client(context: Context): SupabaseClient =
        (context.applicationContext as MyApp).supabase
}
