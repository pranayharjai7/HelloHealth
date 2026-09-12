# ============================================================================
# HelloHealth R8 / ProGuard keep rules
# ----------------------------------------------------------------------------
# R8 full-mode is on (isMinifyEnabled + isShrinkResources). The high-risk paths
# are reflection-driven: kotlinx.serialization DTOs, Supabase/Ktor, Room, and
# Hilt/WorkManager generated code. Everything below keeps only what those need.
# ============================================================================

# --- Kotlin metadata (needed by reflection-based libs) ----------------------
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes InnerClasses,EnclosingMethod,Signature

# --- kotlinx.serialization --------------------------------------------------
# Keep generated serializers and the companion .serializer() accessors.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class **$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Our @Serializable DTOs — keep the classes and their members outright.
-keep @kotlinx.serialization.Serializable class com.hellohealth.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.hellohealth.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# kotlinx.serialization runtime does not need to be warned about.
-dontwarn kotlinx.serialization.**

# --- Ktor / Supabase --------------------------------------------------------
-keep class io.ktor.** { *; }
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**
# Ktor pulls these transitively and references them reflectively.
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**

# --- Room -------------------------------------------------------------------
# Room generates *_Impl classes and needs entity constructors/fields intact.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ----------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

# --- WorkManager (Hilt worker factory instantiates workers reflectively) ----
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class androidx.hilt.work.** { *; }

# --- Our entities/DTOs/domain models (defensive; queried reflectively) ------
-keep class com.hellohealth.data.local.entities.** { *; }
-keep class com.hellohealth.domain.model.** { *; }
