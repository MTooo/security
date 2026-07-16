package io.github.mtooo.core.session;

/**
 * 会话存储接口，定义会话的存取、删除、存在性检查和续期操作。
 *
 * <p>实现类应负责管理 {@link Session} 实例的生命周期，包括过期驱逐和资源清理。
 */
public interface SessionStore {

    /**
     * 根据会话 ID 获取会话。
     *
     * @param sessionId 会话 ID
     * @return 会话对象，如果不存在则返回 {@code null}
     */
    Session get(String sessionId);

    /**
     * 存储会话。如果该会话 ID 已存在，则覆盖原有会话。
     *
     * @param session 会话对象（必须非空）
     */
    void put(Session session);

    /**
     * 根据会话 ID 移除会话。
     *
     * @param sessionId 会话 ID
     */
    void remove(String sessionId);

    /**
     * 检查指定会话 ID 是否存在且未过期。
     *
     * @param sessionId 会话 ID
     * @return 如果存在且未过期则返回 {@code true}，否则返回 {@code false}
     */
    boolean exists(String sessionId);

    /**
     * 续期指定会话，更新其最后访问时间并重置请求计数。
     *
     * <p>如果会话不存在，则该方法无操作。
     *
     * @param sessionId 要续期的会话 ID
     */
    void renew(String sessionId);
}
