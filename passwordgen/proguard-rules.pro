# =============================================================================
# AuRus PasswordGen — R8 / ProGuard (release)
# Модуль автономный: только Compose + HMAC-SHA256 (синхрон с NodePasswordGenerator в :app).
# =============================================================================

# -----------------------------------------------------------------------------
# @Keep (androidx.annotation)
# -----------------------------------------------------------------------------
-keep,allowobfuscation @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class ** { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
}

# -----------------------------------------------------------------------------
# Атрибуты: аннотации, сигнатуры, стеки
# -----------------------------------------------------------------------------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes KotlinMetadata,KotlinClassHeader

# -----------------------------------------------------------------------------
# Meshtastic / JSON / protobuf DTO
# В этом модуле нет wire-моделей и сериализации — только генератор пароля.
# -----------------------------------------------------------------------------

# -----------------------------------------------------------------------------
# Генерация и проверка доступа (HMAC-SHA256) + JCA
# Дублируется явный keep пакета (Kotlin file-level → *Kt).
# -----------------------------------------------------------------------------
-keep class com.example.aura.passwordgen.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# -----------------------------------------------------------------------------
# Bluetooth / сторонние BLE-библиотеки
# Зависимостей BLE нет; оставлены безопасные -dontwarn на платформенные сценарии.
# -----------------------------------------------------------------------------
-keep class android.bluetooth.** { *; }
-keep class android.bluetooth.le.** { *; }
-dontwarn android.bluetooth.**

# -----------------------------------------------------------------------------
# Jetpack Compose (Material3 тянет runtime; без удержания всего androidx.compose)
# -----------------------------------------------------------------------------
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# Общие предупреждения опциональных провайдеров
# -----------------------------------------------------------------------------
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
