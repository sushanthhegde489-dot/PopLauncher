# ProGuard / R8 rules for PopLauncher

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# R8 & Compose handle their own keep rules via consumer AAR rules.
# Do NOT add wildcard `-keep class androidx.compose.** { *; }` as it forces R8
# to retain all ~2000+ unused icons from material-icons-extended and all Compose dead code.

# Data Models serialization / Json parsing safety if needed
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum values for reflection/Enum.valueOf
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Suppress warnings
-dontwarn android.window.**
-dontwarn androidx.window.**
