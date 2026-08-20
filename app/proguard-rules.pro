# Add project specific ProGuard rules here.

# Keep secp256k1 JNI classes
-keep class fr.acinq.secp256k1.** { *; }
-keepclassmembers class fr.acinq.secp256k1.** { *; }

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Room entities
-keep class com.turkbot.babytracker.data.entities.** { *; }

# Suppress R8 missing-class errors for annotations only needed at compile time
-dontwarn com.google.errorprone.annotations.**
