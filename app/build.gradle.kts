import java.util.Properties

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(localFile.inputStream())
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0부터 Compose 컴파일러가 Kotlin 저장소로 이관되어 이 플러그인이 필수
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") // Room 컴파일러
}

/*
 * FCM 즉시 알림(F4)을 쓰려면 Firebase 콘솔에서 받은 google-services.json을
 * app/ 아래에 두면 됩니다. 파일이 있을 때만 플러그인을 적용하므로,
 * 없는 상태에서도 빌드가 그대로 통과합니다 — 그 경우 알림은
 * ControllerWatchWorker의 15분 폴링으로 동작합니다.
 */
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.vatradar.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vatradar.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    // 앱별 언어 설정(AppCompatDelegate.setApplicationLocales)을 Android 13 미만으로 백포트합니다.
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0") // METAR/TAF는 평문 응답
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Room — 전 세계 공항 DB (F3)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore — 설정 (SimBrief ID, 관심 관제소)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager — 관심 관제소 폴링 (F4)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Google Maps Compose (F2)
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("com.google.maps.android:maps-compose-utils:6.4.1") // 마커 클러스터링
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // 이벤트 배너 이미지 (F1)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // SimBrief 디스패치 페이지를 앱 안에서 여는 Custom Tabs (F5)
    implementation("androidx.browser:browser:1.8.0")

    // Firebase Cloud Messaging (F4)
    // 주의: 실제 동작하려면 google-services.json 추가 + google-services 플러그인 적용이 필요합니다.
    //       설정 전에도 빌드는 되며, FCM은 비활성 상태로 남고 WorkManager 폴링이 대신 동작합니다.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Compose 프리뷰
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // 계측 테스트 — assets 실물(FIR 경계, 공항 DB) 검증용
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
