package com.pokai.shorturl.service;

import com.pokai.shorturl.cache.ShortUrlCache;
import com.pokai.shorturl.config.ShortUrlProperties;
import com.pokai.shorturl.dto.CreateShortUrlRequest;
import com.pokai.shorturl.dto.ShortUrlResponse;
import com.pokai.shorturl.entity.UrlMapping;
import com.pokai.shorturl.exception.ShortUrlException;
import com.pokai.shorturl.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final String CODE = "000001";
    private static final String URL = "https://example.com/a/long/path";

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private ShortUrlCache cache;

    @Spy
    private ShortUrlProperties properties = new ShortUrlProperties();

    @InjectMocks
    private ShortUrlService service;

    private UrlMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = UrlMapping.builder()
                .id(1L)
                .shortCode(CODE)
                .originalUrl(URL)
                .customAlias(false)
                .createdAt(LocalDateTime.now())
                .clickCount(0L)
                .build();
    }

    @Test
    @DisplayName("快取命中時不查 DB，但仍累加點擊數")
    void resolve_cacheHit_skipsDatabaseLookup() {
        when(cache.lookup(CODE)).thenReturn(ShortUrlCache.Lookup.hit(URL));

        String result = service.resolveAndCount(CODE);

        assertThat(result).isEqualTo(URL);
        verify(repository, never()).findByShortCode(any());
        verify(repository).incrementClickCount(CODE);
    }

    @Test
    @DisplayName("快取未命中時查 DB，並把結果回填快取")
    void resolve_cacheMiss_fallsBackToDatabaseAndBackfills() {
        when(cache.lookup(CODE)).thenReturn(ShortUrlCache.Lookup.MISS);
        when(repository.findByShortCode(CODE)).thenReturn(Optional.of(mapping));

        String result = service.resolveAndCount(CODE);

        assertThat(result).isEqualTo(URL);
        verify(cache).put(CODE, URL, null);
        verify(repository).incrementClickCount(CODE);
    }

    @Test
    @DisplayName("DB 也查不到時，記住『不存在』擋快取穿透")
    void resolve_notFound_cachesTheMiss() {
        when(cache.lookup(CODE)).thenReturn(ShortUrlCache.Lookup.MISS);
        when(repository.findByShortCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAndCount(CODE))
                .isInstanceOf(ShortUrlException.class)
                .extracting(ex -> ((ShortUrlException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(cache).putMissing(CODE);
    }

    @Test
    @DisplayName("快取記得短碼不存在時，直接回 404 不碰 DB")
    void resolve_knownMissing_doesNotTouchDatabase() {
        when(cache.lookup(CODE)).thenReturn(ShortUrlCache.Lookup.KNOWN_MISSING);

        assertThatThrownBy(() -> service.resolveAndCount(CODE))
                .isInstanceOf(ShortUrlException.class);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("短碼已過期時回 410，並清掉快取")
    void resolve_expired_returnsGoneAndEvicts() {
        mapping.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(cache.lookup(CODE)).thenReturn(ShortUrlCache.Lookup.MISS);
        when(repository.findByShortCode(CODE)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.resolveAndCount(CODE))
                .isInstanceOf(ShortUrlException.class)
                .extracting(ex -> ((ShortUrlException) ex).getStatus())
                .isEqualTo(HttpStatus.GONE);

        verify(cache).evict(CODE);
        verify(repository, never()).incrementClickCount(any());
    }

    @Test
    @DisplayName("建立短網址時用自增 id 產生 Base62 短碼，並預熱快取")
    void create_generatesBase62CodeFromId() {
        CreateShortUrlRequest request = new CreateShortUrlRequest();
        request.setOriginalUrl(URL);

        UrlMapping saved = UrlMapping.builder()
                .id(62L)
                .shortCode("tmp-placeholder")
                .originalUrl(URL)
                .createdAt(LocalDateTime.now())
                .clickCount(0L)
                .build();
        when(repository.saveAndFlush(any())).thenReturn(saved);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.create(request);

        // id=62 → Base62 是 "10"，補到 6 碼變 "000010"
        assertThat(response.getShortCode()).isEqualTo("000010");
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/000010");
        verify(cache).put(eq("000010"), eq(URL), isNull());
    }

    @Test
    @DisplayName("自訂短碼重複時回 409")
    void create_duplicateAlias_returnsConflict() {
        CreateShortUrlRequest request = new CreateShortUrlRequest();
        request.setOriginalUrl(URL);
        request.setCustomAlias("mylink");
        when(repository.existsByShortCode("mylink")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ShortUrlException.class)
                .extracting(ex -> ((ShortUrlException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(repository, never()).save(any());
    }
}
