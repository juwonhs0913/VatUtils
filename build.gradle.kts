// Top-level build file

// oss-licenses-plugin은 플러그인 마커를 퍼블리시하지 않아 plugins {} 로는 못 씁니다.
// 의존성 POM에서 오픈소스 라이선스 원문을 뽑아 앱 리소스로 넣어 주는 플러그인으로,
// Play services 이용 약관이 요구하는 법적 고지를 손으로 옮겨 적지 않아도 됩니다.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
    }
}

// AGP 8.7.2는 Gradle 8.9 기준입니다. gradle-wrapper.properties의 버전과 함께 움직여야 합니다.
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
    // FCM 즉시 알림용. app/google-services.json이 있을 때만 실제로 적용됩니다.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
