# Keep kotlinx.serialization metadata for our @Serializable models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.hotsteel.deepseekhot.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.hotsteel.deepseekhot.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
