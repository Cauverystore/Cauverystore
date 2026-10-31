# Razorpay checkout
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** {*;}
-optimizations !method/inlining/
-keepclasseswithmembers class * { public void onPayment*(...); }

# kotlinx.serialization models
-keepattributes InnerClasses,Signature,Exceptions
-keep,includedescriptorclasses class com.cauverystore.android.data.**$$serializer { *; }
-keepclassmembers class com.cauverystore.android.data.** { *** Companion; }
-keepclasseswithmembers class com.cauverystore.android.data.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.cauverystore.android.data.Api { *; }
