# Xposed / LSPosed 入口类不能被裁剪或混淆
-keep class com.fuoins.opponote.ModuleEntry { *; }
-keep class com.fuoins.opponote.core.** { *; }
-keep class com.fuoins.opponote.hooks.** { *; }

# libxposed 运行时由框架注入，仅编译期引用
-dontwarn io.github.libxposed.**
-dontwarn de.robv.android.xposed.**

# 保留注解信息，便于框架解析
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
