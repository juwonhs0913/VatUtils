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

// 루트 buildscript classpath로 들어온 플러그인이라 id()가 아니라 apply()로 붙입니다.
apply(plugin = "com.google.android.gms.oss-licenses-plugin")

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

/*
 * 릴리스 서명 설정.
 *
 * keystore.properties(.gitignore 대상)에서 읽습니다. 키스토어와 비밀번호는
 * 저장소에 들어가면 안 되고, 키를 잃어버리면 Play에 올린 앱을 다시는 업데이트할 수
 * 없으므로 반드시 따로 백업해 두세요. 만드는 방법은 keystore.properties.example 참고.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(file.inputStream())
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.vatradar.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vatradar.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: 미사용 코드 제거 + 난독화. 리소스 축소까지 함께 켭니다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // 키스토어가 없어도 R8 결과를 실제로 실행해 볼 수 있도록 디버그 키로 서명합니다.
                // 이렇게 만든 APK는 Play에 올릴 수 없습니다(디버그 키는 업로드 시 거부됩니다).
                logger.warn(
                    "keystore.properties가 없어 릴리스 빌드를 디버그 키로 서명합니다. " +
                        "배포용이 아니라 검증용입니다."
                )
                signingConfigs.getByName("debug")
            }
        }

        // debug 빌드에 applicationIdSuffix를 붙이지 않는 이유:
        // google-services.json이 com.vatradar.app 하나로 등록돼 있어 패키지명이
        // 달라지면 Firebase 플러그인이 빌드를 거부하고, Maps 키 제한도 어긋납니다.
    }

    buildFeatures {
        compose = true
        // 설정 화면에 앱 버전을 띄우고 피드백 메일 본문에 넣는 데 씁니다.
        buildConfig = true
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
    // 참고: play-services-oss-licenses는 일부러 넣지 않습니다. 그 라이브러리의 화면은
    //       목록을 Play services에서 받아오는데, 서비스가 없는 기기에서는 빈 화면이 됩니다.
    //       oss-licenses-plugin이 만든 raw 리소스를 OssLicenses가 직접 읽습니다.

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
