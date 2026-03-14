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
