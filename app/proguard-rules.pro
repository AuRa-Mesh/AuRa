# =============================================================================
# AuRus — правила R8 / ProGuard для release
# =============================================================================
# Усиление структуры: переименованные классы сводятся в один пакет (сложнее навигация по dex).
# Типы с -keep / JNI / Room выше не ломаются.
-repackageclasses 'a'

# Дополнительная «зашумленность» имён (агрессивнее, чем по умолчанию — ок для приложения).
-overloadaggressively

# В release убрать verbose/debug логи (меньше утечек в logcat; i/w/e оставляем).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# -----------------------------------------------------------------------------
# Аннотация @Keep (androidx.annotation)
# -----------------------------------------------------------------------------
-keep,allowobfuscation @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class ** { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
}

# -----------------------------------------------------------------------------
# Атрибуты для рефлексии, аннотаций и стеков
# -----------------------------------------------------------------------------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin: метаданные модулей (Compose, корутины, inline)
-keepattributes KotlinMetadata,KotlinClassHeader

# -----------------------------------------------------------------------------
# Meshtastic: модели и кодексы protobuf / JSON по wire-формату
# Не полагаемся на то, что R8 сохранит «кажется неиспользуемые» поля DTO.
# -----------------------------------------------------------------------------
-keep class com.example.aura.meshtastic.** { *; }
-keep class com.example.aura.mesh.protobuf.** { *; }

# JSON (org.json) — ключи строковые в коде; кэш нод на диске.
-keep class com.example.aura.bluetooth.MeshNodeListDiskCache { *; }

# -----------------------------------------------------------------------------
# Криптография и доступ: генерация/проверка ключей (HMAC, SHA-256, EncryptedPrefs)
# Дублируется политика @Keep в исходниках на критичных типах.
# -----------------------------------------------------------------------------
-keep class com.example.aura.security.NodePasswordGenerator { *; }
-keep class com.example.aura.history.MessageHistorySecureAuth { *; }

# javax.crypto / JCA — не вырезать вызовы провайдеров
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# -----------------------------------------------------------------------------
# JNI (Unishox2, Codec2)
# -----------------------------------------------------------------------------
-keepclasseswithmembernames class com.example.aura.meshtastic.Unishox2Native {
    native <methods>;
}
-keepclasseswithmembernames class com.example.aura.voice.Codec2Bridge {
    native <methods>;
}

# -----------------------------------------------------------------------------
# Room
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# MapLibre / Mapbox SDK (нативные слои, рефлексия внутри библиотеки)
# -----------------------------------------------------------------------------
-keep class com.mapbox.** { *; }
-keep interface com.mapbox.** { *; }
-dontwarn com.mapbox.**

# -----------------------------------------------------------------------------
# Coil
# -----------------------------------------------------------------------------
-dontwarn coil.**

# -----------------------------------------------------------------------------
# CameraX / ML Kit / ZXing (часто тянут optional зависимости)
# -----------------------------------------------------------------------------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.video.internal.**
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.odml.**

# -----------------------------------------------------------------------------
# USB Serial for Android
# -----------------------------------------------------------------------------
-keepclassmembers class * extends com.hoho.android.usbserial.driver.UsbSerialPort {
    public <init>(...);
}
-dontwarn com.hoho.android.**

# -----------------------------------------------------------------------------
# NanoHTTPD
# -----------------------------------------------------------------------------
-keep class fi.iki.elonen.** { *; }

# -----------------------------------------------------------------------------
# Bluetooth / BLE (платформа + предупреждения сторонних AAR)
# -----------------------------------------------------------------------------
-keep class android.bluetooth.** { *; }
-keep class android.bluetooth.le.** { *; }
-dontwarn android.bluetooth.**

# -----------------------------------------------------------------------------
# WorkManager / Startup
# -----------------------------------------------------------------------------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.CoroutineWorker
-keep class androidx.startup.** { *; }

# -----------------------------------------------------------------------------
# Общие -dontwarn (сборка без лишних предупреждений от опциональных API)
# -----------------------------------------------------------------------------
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
