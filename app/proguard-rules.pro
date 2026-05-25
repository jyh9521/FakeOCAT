# ── OkHttp & Okio ──
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okio.**

# ── Kotlin 协程 ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── 保留行号信息（便于线上 Crash 定位）──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── DataStore (PreferencesManager 序列化) ──
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── EncryptedSharedPreferences ──
-keep class androidx.security.crypto.** { *; }

# ── Compose ──
-keep class androidx.compose.** { *; }

# ── 保留 Android 入口 ──
-keep class com.example.fakeocat.MainActivity { *; }

# ── 移除 Release 日志（防 API Key 误输出）──
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
