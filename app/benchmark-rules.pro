# Benchmark-specific R8 Proguard rules
# Keep profileable and tracing metadata
-keep class androidx.tracing.** { *; }
-dontwarn androidx.tracing.**
-keep class androidx.metrics.performance.** { *; }
-dontwarn androidx.metrics.performance.**

