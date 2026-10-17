# Keep Kharcha's own code whole: Room, WorkManager, the widget and kotlinx.serialization find
# these classes by name. Only unused library code gets removed.
-keep class app.kharcha.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-dontobfuscate
