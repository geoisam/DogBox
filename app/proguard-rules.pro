-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses
-keepattributes SourceFile, LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations
-keepattributes AnnotationDefault, Exceptions


-keep class kotlin.Metadata { *; }


-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}


-keep,allowobfuscation @interface androidx.compose.runtime.Composable
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}


-keep class kotlinx.serialization.json.** { *; }


-keep class com.tyme.** { *; }
-keep class net.fleeksoft.ksoup.** { *; }

