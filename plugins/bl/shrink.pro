-dontoptimize
-dontobfuscate
-dontwarn **
-keepattributes SourceFile,LineNumberTable
# Preserve the downloaded plugin ABI and stable names used by instrumentation.
-keep class xiao.bu.channel.bl.** { *; }
-keep class com.google.zxing.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
# Exo instantiates its optional FLAC extractor by reflection if included.
# This channel does not ship extension modules or download/UI services.
