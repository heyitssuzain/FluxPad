# FLUXPAD release keep rules
-keep class dev.fluxpad.core.model.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.jetbrains.annotations.**
