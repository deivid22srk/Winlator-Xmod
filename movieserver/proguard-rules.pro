# Add project specific ProGuard rules here.
-keep class com.movielocal.server.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn org.nanohttpd.**
