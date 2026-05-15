# Maksimal obfuskatsiya
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-allowaccessmodification
-mergeinterfacesaggressively

# Android komponentlari — nom o'zgarmasin
-keep public class com.gmailnator.MainActivity { public *; }
-keep public class com.gmailnator.FloatingService { public *; }
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# OkHttp
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

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Barcha qolgan kodni obfuskat qilish
-repackageclasses 'g'
-flattenpackagehierarchy 'g'
