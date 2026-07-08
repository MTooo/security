# ============================================================================
# ProGuard 混淆规则 — SM2 SDK
# ============================================================================
# 策略：库模式（Library Mode）
#   - 保留所有 public/protected 类名和方法签名（外部调用无感知）
#   - 混淆 private 方法、private 字段、内部实现细节
#   - 不收缩、不优化（仅混淆命名）
# ============================================================================

# === 库模式：不删除类、不优化、不预验证，只混淆命名 ===
-dontshrink
-dontoptimize
-dontpreverify

# === 保留调试和运行时属性 ===
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# === 忽略依赖中找不到的类（不会影响混淆安全性） ===
-dontwarn **
-ignorewarnings

# ============================================================================
# 保留所有 public API — 以下规则确保第三方使用完全无感知
# ============================================================================

# --- 所有 public 类/接口：保留 public 和 protected 成员 ---
-keep public class com.sm2sdk.** {
    public protected *;
}

# --- 接口方法全部保留 ---
-keep public interface com.sm2sdk.** {
    *;
}

# --- 枚举：保留 values() 和 valueOf() ---
-keepclassmembers enum com.sm2sdk.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# --- 注解全部保留 ---
-keep @interface com.sm2sdk.** {
    *;
}

# --- 模型类构造器（Jackson 反序列化需要无参构造器） ---
-keepclassmembers class com.sm2sdk.core.model.** {
    public <init>(...);
}

# --- HandshakeResult（内部类，用户直接使用） ---
-keep class com.sm2sdk.core.crypto.Sm2KeyExchange$HandshakeResult {
    public <init>(...);
    public *;
}

# ============================================================================
# 序列化安全 — Jackson / Spring 通过反射访问
# ============================================================================

# Jackson 注解标注的类成员
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.** *;
}

# Spring 注解标注的类和方法（自动配置、请求映射等）
-keepclassmembers class * {
    @org.springframework.** *;
    @org.springframework.boot.** *;
}

# Spring Boot ConfigurationProperties 的 getter/setter
-keepclassmembers class com.sm2sdk.starter.Sm2SdkProperties* {
    public *;
}

# ============================================================================
# 第三方重定位代码（仅 starter 模块） — 完全不动
# ============================================================================
-keep class com.sm2sdk.third.** {
    *;
}
-dontwarn com.sm2sdk.third.**

# ============================================================================
# JDK / 通用保护
# ============================================================================

# native 方法名保留
-keepclasseswithmembernames class * {
    native <methods>;
}

# 序列化 UID
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
