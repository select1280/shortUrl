package com.pokai.shorturl.cache;

import com.pokai.shorturl.config.ShortUrlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 簡易斷路器：Redis 失敗一次後，接下來一段時間直接跳過 Redis。
 * <p>
 * 沒有它的話，Redis 掛掉時每個請求都要各自等 timeout 才放棄，服務會慢到跟掛了差不多，
 * 而且每個請求都印一次 WARN，log 會被洗版。
 * <p>
 * 狀態只用一個 openUntil 表示：
 * <ul>
 *   <li>null：關閉（正常），每次都呼叫 Redis</li>
 *   <li>未來的時間點：開啟，時間到之前一律跳過 Redis</li>
 *   <li>已過去的時間點：半開，放請求去試，成功就關閉、失敗就再開啟一輪</li>
 * </ul>
 * 簡化之處：半開時沒有限制只放一個請求去試。正式環境可以改用 Resilience4j。
 */
@Component
@Slf4j
public class RedisCircuitBreaker {

    private final Clock clock;
    private final Duration openDuration;
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(null);

    public RedisCircuitBreaker(Clock clock, ShortUrlProperties properties) {
        this.clock = clock;
        this.openDuration = properties.getCache().getCircuitOpenDuration();
    }

    /** 現在可以呼叫 Redis 嗎 */
    public boolean allowRequest() {
        Instant until = openUntil.get();
        return until == null || !clock.instant().isBefore(until);
    }

    public void recordSuccess() {
        if (openUntil.getAndSet(null) != null) {
            log.info("Redis 已恢復，斷路器關閉");
        }
    }

    public void recordFailure(RuntimeException ex) {
        Instant previous = openUntil.getAndSet(clock.instant().plus(openDuration));
        // 只在「關閉 → 開啟」那一刻印一次，半開試探失敗不重複印
        if (previous == null) {
            log.warn("Redis 呼叫失敗，斷路器開啟 {} 秒，期間直接查 DB。原因: {}",
                    openDuration.toSeconds(), ex.getMessage());
        }
    }
}
