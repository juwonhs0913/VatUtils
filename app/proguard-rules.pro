# VATRadar R8 규칙
#
# 대부분의 라이브러리는 consumer 규칙을 자체 포함하므로 여기엔 그것만으로 부족한 것만 둡니다.
# 규칙을 추가할 때는 반드시 릴리스 빌드를 실제로 실행해 확인하세요 —
# R8 문제는 컴파일이 아니라 런타임에 드러납니다.

# ---------------------------------------------------------------- kotlinx.serialization
# 직렬화는 클래스 이름이 아니라 컴파일러가 만든 Companion.serializer()를 통해 동작합니다.
# @Serializable 클래스와 그 합성 멤버가 지워지면 런타임에 SerializationException이 납니다.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclassmembers class **$* extends kotlinx.serialization.KSerializer {
    <fields>;
}
-keepclasseswithmembers class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# DTO는 이름이 바뀌어도 무방하지만, 필드가 지워지면 안 됩니다.
-keepclassmembers @kotlinx.serialization.Serializable class com.vatradar.app.data.remote.dto.** {
    <fields>;
}

# ---------------------------------------------------------------- Retrofit
# 인터페이스 메서드의 제네릭 반환 타입(Response<T>, suspend 함수의 Continuation)이
# 지워지면 Retrofit이 컨버터를 고르지 못합니다.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation interface <1>

# 저희 API 인터페이스는 통째로 보존합니다 (개수가 적어 크기 부담이 없습니다).
-keep interface com.vatradar.app.data.remote.** { *; }

# ---------------------------------------------------------------- OkHttp / Okio
# 선택적 플랫폼 기능 참조 경고를 무시합니다 (Android에서는 쓰이지 않는 경로).
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------- Room
# Room이 생성한 구현체는 리플렉션으로 로드됩니다.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------- WorkManager
# Worker는 클래스 이름 문자열로 인스턴스화됩니다.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------- Firebase Messaging
# 매니페스트에 이름으로 등록된 서비스라 지워지면 푸시를 못 받습니다.
-keep class com.vatradar.app.notification.VatRadarMessagingService { *; }

# ---------------------------------------------------------------- 디버깅
# 난독화된 스택트레이스를 되돌릴 수 있도록 줄 번호를 남깁니다.
# (Play Console에 mapping.txt를 올리면 자동으로 복원됩니다)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
