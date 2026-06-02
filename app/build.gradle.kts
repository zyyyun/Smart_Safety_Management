import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    // Phase 7 / 07-03 Wave 3 — supabase-kt 2.2.0 의 @Serializable data class 컴파일러 플러그인
    // (Kotlin 1.9.22 매칭 — libs.versions.toml 의 kotlin 버전과 동일하게 유지)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val kakaoNativeAppKey: String =
    (localProperties.getProperty("kakao.nativeAppKey") ?: "b5282649bc815793990d92669375ea72").trim()

val kakaoRestApiKey: String =
    (localProperties.getProperty("kakao.restApiKey") ?: "549ef0580861ccd75dc20bc5858e349f").trim()

android {
    namespace = "com.example.smart_safety_management"
    compileSdk = 34


    defaultConfig {
        applicationId = "com.example.smart_safety_management"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Phase 7 / D-01 — Supabase Realtime SDK 초기화용 BuildConfig 필드.
        // anon key 는 git 커밋 OK — 보안 경계는 RLS (011_watch_app_rls.sql).
        // 운영 프로젝트 ref = xbjqxnvemcqubjfflain (Smart_Safety_Management, Singapore).
        // (Rule 1 deviation: 07-01-PLAN.md 의 'qjmpxyenkqcdrwnsxvcs' 는 stale.
        //  `supabase projects list` 의 실제 ref 로 정정 — 2026-05-14)
        buildConfigField("String", "SUPABASE_URL", "\"https://xbjqxnvemcqubjfflain.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhianF4bnZlbWNxdWJqZmZsYWluIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYyMzkwMDEsImV4cCI6MjA5MTgxNTAwMX0.o3vm5icG-6Lk6R3cAt-LDh-Gr-HKDn2B0PFVtuFXXus\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", kakaoNativeAppKey.asBuildConfigString())
        buildConfigField("String", "KAKAO_REST_API_KEY", kakaoRestApiKey.asBuildConfigString())
        manifestPlaceholders["kakaoNativeAppKey"] = kakaoNativeAppKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Phase 7 / D-01 (Pitfall 2) — minSdk 24 + supabase-kt minSdk 26 충돌 회피용
        // core library desugaring 필수. desugar_jdk_libs:2.0.4 + 활성화 토글 둘 다 필요.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        // Phase 7 / D-01 — BuildConfig.SUPABASE_URL / SUPABASE_ANON_KEY 노출용
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Phase 7 / 07-03 Task 1 (deviation Rule 3 — environmental block) —
// Project root 가 한글 경로 (D:\2026_산업안전\...) 라서 forked unit-test JVM 의
// argfile classpath 파싱 시 Windows ACP=CP949 로 sun.jnu.encoding 이 초기화되어
// UTF-8 argfile 의 한글 바이트가 깨지고 URLClassLoader 가 ClassNotFoundException 발생.
// `-Dsun.jnu.encoding=UTF-8` 은 argv 파싱 후 적용되므로 fix 안 됨 (JEP 400 한계).
//
// Workaround: app build output 만 ASCII-only 경로 (D:/ssm-app-build) 로 redirect.
// 같은 D: 드라이브 유지 — Kotlin 컴파일러의 relativize() 가 cross-drive 실패 안 함.
// argfile classpath 의 모든 경로가 ASCII 만 포함하게 되어 argv 디코딩 실패가 발생 안 함.
// app/src 와 git repo 는 D:\2026_산업안전\... 그대로 — 시드/스냅샷 영향 0.
//
// 향후 CI/dev 셋업 시 동일 워크어라운드 필요. v1.1 에서 repo 를 ASCII path 로 이동 검토.
layout.buildDirectory.set(file("D:/ssm-app-build"))

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
    implementation("androidx.compose.material3:material3")
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.gson)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // 2026-05-22 — R3 fallback (feature_rtps_test / plan v3.1) RTSP PoC 용.
    // Android 10 (API 29) 에서 ExoPlayer + ImageReader 가 buffer format mismatch
    // (producer output 0x7fa30c06 vs ImageReader RGBA_8888) 로 first frame 캡처
    // 실패 → VideoLAN 공식 LibVLC 의 MediaPlayer.takeSnapshot 으로 교체. ABI 전체
    // 포함 (~25-30MB APK 증가). RtspPocService.kt 만 사용.
    implementation("org.videolan.android:libvlc-all:3.6.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)

    // Play Services Auth
    implementation(libs.play.services.auth.api.phone)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


    // API 요청용 의존성 추가
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")

    // 카카오맵
    implementation("com.kakao.maps.open:android:2.12.8")

    //gis
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Phase 7 / D-01 (amended 2026-05-14) — Supabase Realtime + PostgREST (Kotlin 1.9.22 호환)
    // 금지 (Pitfall 1): supabase-kt 3.x 계열 → Kotlin 2.3.21 강제 (Compose Compiler 1.5.10 ABI 비호환)
    // 금지 (Pitfall 7): ktor okhttp engine → Retrofit 의 OkHttp 4.12.0 와 transitive 충돌, cio engine 사용
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.2.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.2.0")
    // Phase 9 / 09-03 TBM-02 — Storage 모듈 (수기 서명 PNG 업로드용).
    // ABI 호환: realtime-kt + postgrest-kt 2.2.0 와 동일 버전 강제 (Pitfall 1 — 3.x 거부 유지).
    // Transitive: gotrue-kt:2.2.0 + ktor-server-core/cio:2.3.9 자동 (Pitfall 10 acceptable, APK +50KB).
    implementation("io.github.jan-tennert.supabase:storage-kt:2.2.0")
    implementation("io.ktor:ktor-client-cio:2.3.9")

    // 2026-05-21 — Sprint A.2.1: Drift X3 카메라 QR 페어링 (QR 생성 only).
    // core 만으로 BitMatrix → Bitmap 직접 변환 가능, 스캔용 android-embedded 는 불필요.
    implementation("com.google.zxing:core:3.5.3")

    // 2026-05-21 — Sprint A.1.1: Nordic Android BLE library (J2208A 워치 BLE master 포팅).
    //
    // 버전 선정: 2.7.5 (2024-04-29, 1.9-era 마지막 patch).
    // 금지 (Pitfall 1 확장): 2.8.0+ 은 Kotlin 2.x stdlib 를 트랜지티브로 끌고 옴 →
    //   우리의 Compose Compiler 1.5.10 + supabase-kt 2.2.0 Kotlin 1.9.22 lock 과 충돌.
    //   증상: "Class 'kotlin.Unit' was compiled with an incompatible version of Kotlin.
    //   The actual metadata version is 2.2.0, but the compiler version 1.9.0 can read
    //   versions up to 2.0.0."
    //   A.1 sprint 가 끝날 때까지 2.7.x 라인 유지. 향후 전체 Kotlin 2.x 마이그레이션
    //   시점에 한꺼번에 bump.
    implementation("no.nordicsemi.android:ble-ktx:2.7.5")
    implementation(files("libs/2208asdk2.0.jar"))

    // 2026-05-21 — 트랜지티브 stdlib defensive lock (모든 dep 의 transitive Kotlin stdlib
    // 을 1.9.22 로 강제). 새 dep 추가 시 위 metadata 충돌 재발 방지.
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22") {
            because("프로젝트 Kotlin 1.9.22 lock — Compose Compiler 1.5.10 ABI 호환 유지")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22") {
            because("same as above")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22") {
            because("same as above")
        }
    }

    // Phase 7 / Pitfall 2 — minSdk 24 + supabase-kt minSdk 26 → core library desugaring 필수
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

}
