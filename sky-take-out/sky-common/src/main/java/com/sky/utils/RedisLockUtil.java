package com.sky.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Redis 分布式锁工具类
 * 基于 SET NX PX 原子命令 + Lua 脚本安全释放
 */
@Slf4j
public class RedisLockUtil {

    /**
     * 释放锁的 Lua 脚本（原子性校验 value 后删除）：
     * KEYS[1] = 锁的 key
     * ARGV[1] = 锁的 value（requestId）
     */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "   return redis.call('del', KEYS[1]) " +
            "else " +
            "   return 0 " +
            "end";

    /**
     * 尝试获取分布式锁
     *
     * @param redisTemplate Redis 模板
     * @param lockKey       锁的 key
     * @param timeout       锁的过期时间（防止死锁）
     * @return requestId（释放锁时需要传入），获取失败返回 null
     */
    public static String tryLock(RedisTemplate<String, Object> redisTemplate,
                                 String lockKey, Duration timeout) {
        String requestId = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, requestId, timeout);
        if (Boolean.TRUE.equals(locked)) {
            log.debug("获取分布式锁成功: lockKey={}, requestId={}", lockKey, requestId);
            return requestId;
        }
        log.debug("获取分布式锁失败: lockKey={}", lockKey);
        return null;
    }

    /**
     * 释放分布式锁（安全：只释放自己持有的锁）
     *
     * @param redisTemplate Redis 模板
     * @param lockKey       锁的 key
     * @param requestId     获取锁时返回的 requestId
     * @return true=释放成功，false=锁已过期或被他人持有
     */
    public static boolean unlock(RedisTemplate<String, Object> redisTemplate,
                                 String lockKey, String requestId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(UNLOCK_LUA);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                requestId
        );

        boolean success = result != null && result == 1L;
        if (success) {
            log.debug("释放分布式锁成功: lockKey={}", lockKey);
        } else {
            log.warn("释放分布式锁失败（锁已过期或被他人持有）: lockKey={}", lockKey);
        }
        return success;
    }
}
