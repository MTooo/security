package com.sm2sdk.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;

    public Sm2EncryptedBodyConverter() {
        this.objectMapper = new ObjectMapper();
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
        String body = new String(bytes, StandardCharsets.UTF_8);
        log.debug("Sm2EncryptedBodyConverter.read: {} bytes, targetType={}", bytes.length, clazz);

        if (clazz == String.class) {
            return body;
        }
        return objectMapper.readValue(body, clazz);
    }

    @Override
    public void write(Object o, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        throw new UnsupportedOperationException("Sm2EncryptedBodyConverter 不支持写入");
    }
}
