package com.smartats.module.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Cache eviction service (separate Service so the @Async proxy takes effect)
 * <p>
 * Key points of the delayed double-delete strategy:<br>
 * 1. Delete the cache before the write operation<br>
 * 2. Delete it again asynchronously after a delay (evicts stale cache rebuilt within the concurrency window)<br>
 * <p>
 * ⚠️ Spring @Async only takes effect via the proxy on **cross-Bean calls**.
 * If asyncDeleteCache() were placed in and called from the same JobService class, it would run synchronously.
 * That is why it must live in a separate Service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheEvictionService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Asynchronously delete the cache with a delay (second step of the delayed double-delete)
     *
     * @param cacheKey cache Key
     */
    @Async("asyncExecutor")
    public void asyncDeleteCache(String cacheKey) {
        try {
            // Delay 500ms (longer than the average read request duration, ensuring the stale cache has been written)
            Thread.sleep(500);

            redisTemplate.delete(cacheKey);
            log.debug("Delayed double-delete completed (pass 2): key={}", cacheKey);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Delayed cache deletion interrupted: key={}", cacheKey, e);
        }
    }
}