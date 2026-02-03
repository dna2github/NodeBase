# Netty
-keepclassmembers class io.netty.** { *; }
-keep class io.netty.** { *; }
-dontwarn io.netty.**

# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements java.io.Serializable { *; }

# Protocol classes
-keep class seven.lab.wstun.protocol.** { *; }

# Server classes
-keep class seven.lab.wstun.server.** { *; }
