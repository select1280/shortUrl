package com.pokai.shorturl.cache;

import com.pokai.shorturl.config.ShortUrlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ShortUrlCacheTest {

    private static final String CODE = "000001";
    private static final String KEY = "shorturl:code:" + CODE;
    private static final String URL = "https://example.com/a/long/path";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ShortUrlProperties properties;
    private ShortUrlCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        properties = new ShortUrlProperties();
        cache = new ShortUrlCache(redis, properties);
    }

    @Test
    @DisplayName("快取有值時回 HIT")
    void lookup_returnsHit() {
        when(valueOps.get(KEY)).thenReturn(URL);

        ShortUrlCache.Lookup lookup = cache.lookup(CODE);

        assertThat(lookup.status()).isEqualTo(ShortUrlCache.Status.HIT);
        assertThat(lookup.originalUrl()).isEqualTo(URL);
    }

    @Test
    @DisplayName("快取沒值時回 MISS")
    void lookup_returnsMiss() {
        when(valueOps.get(KEY)).thenReturn(null);

        assertThat(cache.lookup(CODE).status()).isEqualTo(ShortUrlCache.Status.MISS);
    }

    @Test
    @DisplayName("putMissing 寫入的標記，讀回來要能認出是『確定不存在』")
    void missingMarker_roundTrips() {
        cache.putMissing(CODE);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(KEY), value.capture(), eq(Duration.ofSeconds(60)));

        when(valueOps.get(KEY)).thenReturn(value.getValue());
        assertThat(cache.lookup(CODE).status()).isEqualTo(ShortUrlCache.Status.KNOWN_MISSING);
    }

    @Test
    @DisplayName("Redis 連線失敗時當成 MISS，不能讓例外往外拋")
    void lookup_degradesGracefullyOnRedisFailure() {
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(cache.lookup(CODE).status()).isEqualTo(ShortUrlCache.Status.MISS);
    }

    @Test
    @DisplayName("寫入失敗也不能拋例外，快取只是加速手段")
    void put_degradesGracefullyOnRedisFailure() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        cache.put(CODE, URL, null);   // 不應該拋出
    }

    @Test
    @DisplayName("沒有有效期限時用設定的 TTL")
    void put_usesConfiguredTtlWhenNoExpiry() {
        cache.put(CODE, URL, null);

        verify(valueOps).set(KEY, URL, Duration.ofHours(1));
    }

    @Test
    @DisplayName("有效期限比 TTL 早時，TTL 要縮短到剩餘時間，避免導向已過期的連結")
    void put_clampsTtlToExpiry() {
        cache.put(CODE, URL, LocalDateTime.now().plusMinutes(5));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(KEY), eq(URL), ttl.capture());
        assertThat(ttl.getValue()).isLessThanOrEqualTo(Duration.ofMinutes(5));
        assertThat(ttl.getValue()).isGreaterThan(Duration.ofMinutes(4));
    }

    @Test
    @DisplayName("已經過期的短碼不寫進快取")
    void put_skipsWhenAlreadyExpired() {
        cache.put(CODE, URL, LocalDateTime.now().minusMinutes(1));

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("關閉快取時完全不碰 Redis")
    void disabledCache_bypassesRedisEntirely() {
        properties.getCache().setEnabled(false);

        assertThat(cache.lookup(CODE).status()).isEqualTo(ShortUrlCache.Status.MISS);
        cache.put(CODE, URL, null);
        cache.putMissing(CODE);
        cache.evict(CODE);

        verifyNoInteractions(valueOps);
        verify(redis, never()).delete(anyString());
    }
}
