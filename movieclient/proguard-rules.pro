# Add project specific ProGuard rules here.
-keep class com.movielocal.client.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn retrofit2.**
-dontwarn okhttp3.**
