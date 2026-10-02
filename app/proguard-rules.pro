-repackageclasses ''
-allowaccessmodification

# JNI entry points. AGP's default proguard-android-optimize.txt keeps
# `native <methods>` names (keepclasseswithmembernames), but that default file
# is an implementation detail of the AGP version — pin the rule here too so
# libmikubox_core's Java_com_mikubox_mihomo_core_MihomoCore_* bindings survive
# regardless of it. -dontobfuscate below keeps stack traces readable on top.
-keepclasseswithmembernames class com.mikubox.mihomo.core.MihomoCore {
    native <methods>;
}

-keep class com.yalantis.ucrop.UCropActivity { *; }

# Gson builds these models reflectively. When R8 drops a model's constructor
# (nothing in code calls it), Gson falls back to Unsafe.allocateInstance, which
# never runs the constructor — Kotlin field defaults (an empty id, empty
# lists) are then missing and null reaches code compiled for non-null. That is
# how a fresh release install crashed at Application.onCreate
# (RulesetItem.id.trim()) and how reflection-parsed results came back with
# unset fields. Keep the constructors and the fields Gson fills.
-keepclassmembers class com.miku.ray.dto.** {
    <init>(...);
    <fields>;
}
-keepclassmembers class com.miku.ray.ui.weather.** {
    <init>(...);
    <fields>;
}

# Clean Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
}

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

-dontobfuscate
-keepattributes SourceFile

-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.beans.Transient
-dontwarn java.beans.VetoableChangeListener
-dontwarn java.beans.VetoableChangeSupport
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontwarn org.bouncycastle.jce.provider.BouncyCastleProvider
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn java.beans.PropertyVetoException
