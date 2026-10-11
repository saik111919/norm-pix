# Keep rules for libraries that need them are added here as they come up.

# ML Kit (card scanning) lists its components in the manifest and creates them by reflection. R8 must keep their
# no-argument constructors, or text recognition never initialises ("NoSuchMethodException: ...Registrar.<init>").
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
