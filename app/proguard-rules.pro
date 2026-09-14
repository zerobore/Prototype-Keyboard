# Prototype Keyboard ProGuard / R8 rules.
# Release builds use R8 full mode via proguard-android-optimize.txt (see app/build.gradle.kts).

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# InputMethodService + Activity are referenced from AndroidManifest.xml and kept automatically.
# Datastore / Compose / Material3 ship their own consumer rules.
# Phase 3: Room entities will use @Keep on the entity classes instead of rules here.
