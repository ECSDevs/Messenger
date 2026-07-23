# ---------------------------------------------------------------------------
# Messenger (shared KMP library) R8/ProGuard rules
# This module contains domain/data/presentation code shared between the
# Android and Desktop app shells. Full app-level rules (manifest components,
# Service/Activity subclasses) live in androidApp/proguard-rules.pro.
# ---------------------------------------------------------------------------

# --- Annotations / signatures / inner-class metadata -----------------------
# Gson needs Signature to resolve generic types; Retrofit & Room need
# RuntimeVisibleAnnotations to read @POST/@Query/@Entity etc.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes AnnotationDefault,Exceptions,Deprecated
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===========================================================================
# Room
# ===========================================================================
-keep class cc.ptoe.messenger.data.local.MessengerDatabase { *; }
-keep class cc.ptoe.messenger.data.local.MessengerDatabase_* { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep interface cc.ptoe.messenger.data.local.dao.* { *; }
-keep @androidx.room.Entity class * { *; }
-keep class androidx.room.RoomSQLiteQuery { *; }
-keep class * extends androidx.room.util.ShortcutUtil { *; }
-dontwarn androidx.room.**
-dontwarn androidx.room.paging.**

# ===========================================================================
# Cloud authentication and sync models (serialized reflectively via Gson)
# ===========================================================================
-keep class cc.ptoe.messenger.data.cloud.** { *; }
-keep class cc.ptoe.messenger.data.remote.dto.** { *; }
-keepclassmembers class cc.ptoe.messenger.data.remote.dto.** { *; }

# ===========================================================================
# llm-typewriter / AndroidMath (Markdown + LaTeX rendering)
# ===========================================================================
-keep class cc.ptoe.llmtypewriter.** { *; }
-keep class com.agog.mathdisplay.** { *; }
-keep class com.pvporbit.freetype.** { *; }
-dontwarn cc.ptoe.llmtypewriter.**
-dontwarn com.agog.mathdisplay.**
-dontwarn com.pvporbit.freetype.**

# ===========================================================================
# MobileHttpServer / MobileWearChatHandler (Wear bridge, lives in shared)
# ===========================================================================
-keep class cc.ptoe.messenger.data.wear.MobileHttpServer { *; }
-keep class cc.ptoe.messenger.data.wear.MobileHttpServer$* { *; }
-keep class cc.ptoe.messenger.data.wear.MobileWearChatHandler { *; }
