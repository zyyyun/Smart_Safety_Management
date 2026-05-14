# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ──────────────────────────────────────────
# Phase 7 / D-01 — Supabase-kt + Ktor reflection keep rules (Pitfall 8)
# ──────────────────────────────────────────
# supabase-kt 2.2.0 + ktor-client-cio 2.3.9 + kotlinx.serialization 가 reflection
# 으로 모델 직렬화/역직렬화. release build (minify on) 시 R8 strip 방지.
# 현재 build.gradle.kts:31 의 isMinifyEnabled = false 라 즉시 효력 없음 — v1.1
# minify 활성화 대비 선반영.
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt