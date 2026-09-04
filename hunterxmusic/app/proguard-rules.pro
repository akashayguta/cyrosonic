# ============================================================================
#  CyroSonic — R8 / ProGuard keep rules for the RELEASE build
#  minifyEnabled + shrinkResources are ON in build.gradle.kts (release).
#  These rules protect the reflection-based libraries from being renamed or
#  stripped. Conservative on purpose: this project can't be test-shrunk here,
#  so we keep generously rather than risk a runtime crash in the signed APK.
# ============================================================================

# Keep source file + line numbers so release crash stack traces are readable,
# but hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures, annotations and inner-class info (needed by Gson,
# Retrofit and Kotlin reflection to resolve types at runtime).
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault,*Annotation*

# ----------------------------------------------------------------------------
#  Strip verbose logging in release. Log.d / Log.v carry no crash value and
#  may leak stream URLs / video IDs; Log.w / Log.e are KEPT for diagnostics.
# ----------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static boolean isLoggable(java.lang.String, int);
}

# ----------------------------------------------------------------------------
#  App classes kept for reflection (Gson models, Room entities, Media3
#  service). Deliberately NOT a blanket `com.example.hunterxmusic.**` keep —
#  that would disable obfuscation for the entire app. Only the layers that
#  genuinely need runtime reflection stay visible.
# ----------------------------------------------------------------------------
-keep class com.example.hunterxmusic.domain.model.** { *; }
-keep class com.example.hunterxmusic.domain.repository.** { *; }
-keep class com.example.hunterxmusic.core.** { *; }
-keep class com.example.hunterxmusic.data.remote.** { *; }
-keep class com.example.hunterxmusic.data.local.db.** { *; }
-keep class com.example.hunterxmusic.data.player.** { *; }
-keep class com.example.hunterxmusic.service.** { *; }

# Any field explicitly tagged for Gson, wherever it lives.
-keepclassmembers class com.example.hunterxmusic.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ----------------------------------------------------------------------------
#  Coil & Palette (Image loading & dynamic artwork palette extraction)
# ----------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**
-keep class androidx.palette.** { *; }
-dontwarn androidx.palette.**

# ----------------------------------------------------------------------------
#  Room entities / DAOs (Room generates code; entities already kept above,
#  this guards the generated DAO impls referenced from the db package).
# ----------------------------------------------------------------------------
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ============================================================================
#  Gson
# ============================================================================
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class sun.misc.Unsafe { *; }
-dontwarn sun.misc.**

# ============================================================================
#  Retrofit 2 + OkHttp + Okio  (reflection on service interfaces / annotations)
# ============================================================================
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep the app's own Retrofit service interfaces and their annotations.
-keep interface com.example.hunterxmusic.data.remote.*Service { *; }
-keepclasseswithmembers interface com.example.hunterxmusic.data.remote.** {
    @retrofit2.http.* <methods>;
}
-keepattributes Exceptions

-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
# OkHttp platform code references Conscrypt / BouncyCastle optionally.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================================================
#  NewPipeExtractor (org.schabi.newpipe.*) — heavy reflection + its transitive
#  deps (Rhino JS engine, nanojson, jsoup). Keep broadly; this is the fragile
#  one and cannot be tested here.
# ============================================================================
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ============================================================================
#  Media3 / ExoPlayer ships its own consumer rules; add a guard for the
#  OkHttp datasource + our custom DataSource/Player subclasses.
# ============================================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.example.hunterxmusic.data.player.EncryptedDataSource { *; }
-keep class com.example.hunterxmusic.data.player.** { *; }

# ============================================================================
#  Kotlin runtime / coroutines / metadata
# ============================================================================
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlin.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Keep enum special methods (Gson/Compose rely on values()/valueOf()).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
#  Jetpack Compose keeps itself via bundled rules; no extra rules required.
#  AndroidKeyStore crypto (CryptoManager) uses framework APIs only — safe.
# ============================================================================

# ============================================================================
#  THEME / SKIN SYSTEM — CRASH FIX (Settings scroll NPE)
#  R8 full-mode horizontal class merging was collapsing the Skin sealed-class
#  object instances (MIDNIGHT/OCEAN/...) and their static INSTANCE fields came
#  out null in the release binary → "Skin.getId() on a null object reference"
#  the moment the Themes & Skins section composed (LazyColumn prefetch fires
#  it mid-scroll). Keeping the whole theme package intact pins those
#  instances. Also covers ThemeManager's Compose state delegates.
# ============================================================================
-keep class com.example.hunterxmusic.theme.** { *; }
-keep class com.example.hunterxmusic.data.local.ThemeManager { *; }
-keep class com.example.hunterxmusic.data.local.ThemePrefs { *; }
-keepclassmembers class com.example.hunterxmusic.theme.Skin$* {
    public static ** INSTANCE;
}
# Belt & suspenders: disable horizontal merging for the theme classes even if
# a future AGP changes keep semantics.
-if class com.example.hunterxmusic.theme.Skin$*
-keep class com.example.hunterxmusic.theme.Skin$*
