# Obfuskatsiya — lekin app ishlaydi
-optimizationpasses 3
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-allowaccessmodification

# Android komponentlari (manifest bilan bog'liq)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep class com.gmailnator.MainActivity { *; }
-keep class com.gmailnator.FloatingService { *; }

# View'lar va layout
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclassmembers class * extends android.view.View {
    void set*(***);
    *** get*();
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
