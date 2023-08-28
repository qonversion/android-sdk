-keep class com.qonversion.** { *; }

# Retrofit obfucation fix for Java 18 + Gradle 8
# todo remove after upgrading retrofit to a version including this commit - https://github.com/square/retrofit/commit/ef8d867ffb34b419355a323e11ba89db1904f8c2
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response
