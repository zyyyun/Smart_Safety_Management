plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    // Phase 7 / 07-03 Wave 3 — supabase-kt 2.2.0 의 @Serializable data class 컴파일러 플러그인
    // (Kotlin 1.9.22 매칭 — libs.versions.toml 의 kotlin 버전과 동일하게 유지)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

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
    implementation("androidx.media3:media3-ui:1.4.1")
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
    implementation("io.ktor:ktor-client-cio:2.3.9")

    // Phase 7 / Pitfall 2 — minSdk 24 + supabase-kt minSdk 26 → core library desugaring 필수
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

}
