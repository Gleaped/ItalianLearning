# ---- kotlinx.serialization ----
# The library ships consumer rules; these cover our own @Serializable models,
# including the hand-written JsonContentPolymorphicSerializer for exercises.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gleaped.italianlearning.**$$serializer { *; }
-keepclassmembers class com.gleaped.italianlearning.** {
    *** Companion;
}
-keepclasseswithmembers class com.gleaped.italianlearning.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.gleaped.italianlearning.content.** { *; }

# ---- Room ----
# Room ships consumer rules; keep the generated database implementation and
# entities/DAOs explicitly as a belt-and-braces measure.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---- Broadcast receivers referenced from the manifest are kept by AAPT rules ----
