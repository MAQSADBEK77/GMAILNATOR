# Obfuskatsiya — lekin app ishlaydi
-optimizationpasses 3
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-allowaccessmodification

# Android komponentlari
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep class com.gmailnator.auto.MainActivity { *; }
-keep class com.gmailnator.auto.TelegramAutoService { *; }

# View'lar
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# R sinfi
-keepclassmembers class **.R$* { public static <fields>; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# JSON
-keep class org.json.** { *; }

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
