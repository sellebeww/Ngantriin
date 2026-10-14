# R8 rules for the release build. Most libraries here (Room, WorkManager,
# Compose, Firebase, supabase-kt, Ktor) ship their own consumer rules; what
# is below covers what they leave out.

# Stack traces in crash reports stay readable after obfuscation.
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,EnclosingMethod,Signature
-renamesourcefileattribute SourceFile

# kotlinx.serialization: the generated $$serializer / Companion.serializer()
# are looked up reflectively for the Supabase DTOs.
-keepclassmembers @kotlinx.serialization.Serializable class org.umn.ngantriin.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.umn.ngantriin.**$$serializer { *; }

# Ktor's JVM engine references classes that do not exist on Android.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn io.netty.**
-dontwarn reactor.blockhound.**
