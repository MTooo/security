package com.sm2sdk.starter;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * SM2 密文请求体 HTTP 消息转换器（jakarta.servlet 版本）。
 *
 * <p>客户端加密请求体以 {@code Content-Type: text/plain} 发送（Base64 密文）。
 * 默认 Spring Jackson 转换器不接受 {@code text/plain}，会返回 415。
 *
 * <p>此转换器：
 * <ol>
 *   <li>支持 {@code text/plain} → 任意类型（使请求不被 415 拒绝）</li>
 *   <li>将请求体作为字符串读取，委托给 Jackson 反序列化</li>
 * </ol>
 *
 * <p><b>解密由 {@link Sm2RequestBodyAdvice#beforeBodyRead} 在转换器读取前完成。</b>
 * 因此此转换器收到的已是解密后的 JSON 字符串。
 *
 * @see Sm2RequestBodyAdvice
 */
public class Sm2EncryptedBodyConverter implements HttpMessageConverter<Object> {

    private static final Logger log = LoggerFactory.getLogger(Sm2EncryptedBodyConverter.class);

    /** 请求体最大大小（1MB，安全防护） */
    private static final int MAX_BODY_SIZE = 1_000_000;

    public Sm2EncryptedBodyConverter() {
        // JSON 序列化使用 Hutool JSONUtil，无需预初始化
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return mediaType != null
                && MediaType.TEXT_PLAIN.includes(mediaType)
                && clazz != String.class;
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return Collections.singletonList(MediaType.TEXT_PLAIN);
    }

    @Override
    public Object read(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {

        byte[] bytes = inputMessage.getBody().readAllBytes();
        if (bytes.length == 0) {
            return null;
        }
        // 安全防护：限制请求体最大 1MB
        if (bytes.length > MAX_BODY_SIZE) {
            throw new HttpMessageNotReadableException(
                    "请求体过大: " + bytes.length + " bytes (最大 " + MAX_BODY_SIZE + ")", inputMessage);
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        log.debug("Sm2EncryptedBodyConverter.read: {} bytes, targetType={}", bytes.length, clazz);

        if (clazz == String.class) {
            return body;
        }
        return JSONUtil.toBean(body, clazz);
    }

    @Override
    public void write(Object o, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        throw new UnsupportedOperationException("Sm2EncryptedBodyConverter 不支持写入");
    }
}
