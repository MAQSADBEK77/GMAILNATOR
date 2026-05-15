# Barcha sinf/metod nomlarini o'zgartiradi (maksimal himoya)
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
-mergeinterfacesaggressively

# AccessibilityService — Android tizimi chaqiradi, nom o'zgarmasin
-keep public class com.gmailnator.auto.TelegramAutoService {
    public *;
}
-keep public class com.gmailnator.auto.MainActivity {
    public *;
}

# Companion object konstantalari
-keepclassmembers class com.gmailnator.auto.TelegramAutoService {
    public static final java.lang.String ACTION_STOP;
    public static final java.lang.String ACTION_RESTART;
}

# Android komponentlari
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep public class * extends android.content.BroadcastReceiver

# OkHttp — tarmoq kutubxonasi
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# JSON
-keep class org.json.** { *; }

# Reflection orqali ishlatiladigan narsalar
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Barcha qolgan kodlarni obfuskat qiladi
-repackageclasses 'x'
-flattenpackagehierarchy 'x'
