# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson serialization classes
-keepattributes Signature
-keepattributes *Annotation*

# Keep data classes
-keep class com.longyannfc.app.data.remote.** { *; }
-keep class com.longyannfc.app.data.local.** { *; }

# Keep Crypto1 and crypto classes
-keep class com.longyannfc.app.utils.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# Timber
-dontwarn timber.**
-keep class timber.** { *; }

# Tink（androidx.security:security-crypto 依赖）
# ErrorProne 注解仅在编译期使用，运行期不存在，缺失不会影响功能
-dontwarn com.google.errorprone.annotations.**
