# 保留 Hilt 生成的组件与 Compose 相关反射入口
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepattributes *Annotation*
