// Top-level build file
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
