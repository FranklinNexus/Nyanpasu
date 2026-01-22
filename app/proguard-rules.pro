# ===========================
# Nyanpasu Wallpaper ProGuard Rules
# ===========================
# This file configures code obfuscation and optimization for the release build.

# --- Keep Line Numbers for Crash Reports ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Keep Annotations ---
-keepattributes *Annotation*

# --- Keep Application Class ---
-keep public class * extends android.app.Application

# --- Keep WorkManager Workers ---
-keep class com.kuroshimira.nyanpasu.WallpaperWorker { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# --- Keep Kotlin Coroutines ---
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Keep Coil Image Loading Library ---
-dontwarn coil.**
-keep class coil.** { *; }

# --- Keep Jsoup HTML Parser ---
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# --- Keep PhotoView Library ---
-keep class com.github.chrisbanes.photoview.** { *; }
-dontwarn com.github.chrisbanes.photoview.**

# --- Keep Data Classes and Models ---
-keep class com.kuroshimira.nyanpasu.ImageProcessor { *; }

# --- Keep Wallpaper Manager ---
-keep class android.app.WallpaperManager { *; }

# --- Keep Serializable Classes ---
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- Remove Logging in Release ---
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# --- Keep ViewBinding Classes ---
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}

# --- General Android Rules ---
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.appcompat.app.AppCompatActivity

# --- Remove Unused Resources (R8 Optimization) ---
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# --- Keep Enum Classes ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===========================
# End of ProGuard Rules
# ===========================
