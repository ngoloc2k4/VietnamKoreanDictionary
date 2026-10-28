# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable,*Annotation*

# ==========================================
# 1. Room Database & SQLite
# ==========================================
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ==========================================
# 2. Data Models, AI Configurations & Entities
# ==========================================
-keep class com.example.data.model.** { *; }
-keep class com.example.data.db.** { *; }
-keep class com.example.data.initial.** { *; }
-keep class com.example.ai.gateway.** { *; }
-keep class com.example.ai.tier1.** { *; }
-keep class com.example.ai.tier2.** { *; }
-keep class com.example.util.** { *; }

# ==========================================
# 3. Moshi & JSON Parsing
# ==========================================
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-dontwarn com.squareup.moshi.**

# ==========================================
# 4. Kotlin Coroutines & Flow
# ==========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==========================================
# 5. OkHttp & Okio
# ==========================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ==========================================
# 6. Jetpack Compose & Material 3
# ==========================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
