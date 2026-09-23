package com.pokai.shorturl.cache;

import com.pokai.shorturl.config.ShortUrlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用假的 Clock 控制時間，驗證「30 秒後」的行為不用真的等 30 秒。
 */
class RedisCircuitBreakerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final RuntimeException REDIS_DOWN = new RuntimeException("redis down");

    private Clock clock;
    private RedisCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(T0);
        // 預設開啟時間 30 秒
        breaker = new RedisCircuitBreaker(clock, new ShortUrlProperties());
    }

    private void advanceSeconds(long seconds) {
        when(clock.instant()).thenReturn(T0.plusSeconds(seconds));
    }

    @Test
    @DisplayName("初始狀態允許呼叫 Redis")
    void initiallyClosed() {
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("失敗後斷路器開啟，期間不呼叫 Redis")
    void opensAfterFailure() {
        breaker.recordFailure(REDIS_DOWN);

        assertThat(breaker.allowRequest()).isFalse();
        advanceSeconds(29);
        assertThat(breaker.allowRequest()).isFalse();
    }

    @Test
    @DisplayName("開啟時間過了之後放行，讓請求去試 Redis 是否恢復")
    void allowsTrialAfterOpenDuration() {
        breaker.recordFailure(REDIS_DOWN);

        advanceSeconds(30);
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("試探失敗會重新開啟一輪")
    void reopensWhenTrialFails() {
        breaker.recordFailure(REDIS_DOWN);
        advanceSeconds(30);

        breaker.recordFailure(REDIS_DOWN);

        assertThat(breaker.allowRequest()).isFalse();
        advanceSeconds(59);
        assertThat(breaker.allowRequest()).isFalse();
        advanceSeconds(60);
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("試探成功就關閉，恢復正常")
    void closesWhenTrialSucceeds() {
        breaker.recordFailure(REDIS_DOWN);
        advanceSeconds(30);

        breaker.recordSuccess();

        // 回到關閉狀態：就算時間倒回開啟期間也照樣放行
        advanceSeconds(1);
        assertThat(breaker.allowRequest()).isTrue();
    }
}
