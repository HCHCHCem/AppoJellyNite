# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.appojellyapp.core.model.** { *; }
-keep class com.appojellyapp.feature.**.data.** { *; }

# Apollo GraphQL
-keep class com.appojellyapp.feature.playnite.graphql.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Jellyfin SDK
-keep class org.jellyfin.sdk.model.** { *; }
-keep class org.jellyfin.sdk.api.** { *; }
-dontwarn org.jellyfin.sdk.**

# Coil
-dontwarn coil.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Leanback
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**
