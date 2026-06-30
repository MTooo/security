# SM2 SDK ProGuard 混淆配置
# 保护核心加解密逻辑，保持公开 API 不变

# ====== 保持公开 API ======
-keep public class com.sm2sdk.core.crypto.Sm2KeyExchange { public *; }
-keep public class com.sm2sdk.core.crypto.Sm4Crypto { public *; }
-keep public class com.sm2sdk.core.crypto.KeyDerivation { public *; }
-keep public class com.sm2sdk.core.session.Session { public *; }
-keep public class com.sm2sdk.core.session.SessionStore { public *; }
-keep public class com.sm2sdk.core.session.SessionManager { public *; }
-keep public class com.sm2sdk.core.nonce.NonceValidator { public *; }
-keep public class com.sm2sdk.core.model.** { *; }
-keep public class com.sm2sdk.core.exception.** { *; }
-keep public class com.sm2sdk.core.util.Sm2KeyGen { public *; }

# ====== 保持客户端 API ======
-keep public class com.sm2sdk.client.Sm2HttpClient { public *; }
-keep public class com.sm2sdk.client.Sm2Request { public *; }
-keep public class com.sm2sdk.client.Sm2ClientConfig { public *; }
-keep public class com.sm2sdk.client.HandshakeRetryHandler { public *; }

# ====== 保持 Spring Boot Starter ======
-keep public class com.sm2sdk.starter.Sm2SdkAutoConfiguration { *; }
-keep public class com.sm2sdk.starter.Sm2SdkProperties { *; }
-keep public class com.sm2sdk.starter.Sm2HandshakeController { *; }
-keep public class com.sm2sdk.starter.Sm2ServerInterceptor { *; }
-keep public class com.sm2sdk.starter.Sm2ResponseBodyAdvice { *; }
-keep public class com.sm2sdk.starter.Sm2ServerConfig { *; }

# ====== 保持 Spring 注解 ======
-keepattributes *Annotation*
-keep class org.springframework.** { *; }
-dontwarn org.springframework.**

# ====== 保持 JSON 序列化 ======
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
}
-keep @com.fasterxml.jackson.annotation.JsonIgnore class * { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# ====== 保持序列化相关 ======
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ====== 保持 SLF4J ======
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# ====== 保持枚举 ======
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ====== 通用优化 ======
-optimizationpasses 3
-dontusemixedcaseclassnames
-verbose

# 移除调试信息（生产环境）
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# 不警告缺少的类（provided 依赖）
-dontwarn javax.servlet.**
-dontwarn jakarta.servlet.**
-dontwarn io.lettuce.**
-dontwarn org.springframework.data.redis.**
