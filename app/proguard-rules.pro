# Keep Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Hilt / Dagger generated classes
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# Google Tink Cryptography
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# AndroidX Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil 3 Image Loader
-keep class coil3.** { *; }
-dontwarn coil3.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Preserve line numbers for clean local crash inspection
-keepattributes SourceFile,LineNumberTable
