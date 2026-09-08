package com.pokai.shorturl.service;

import com.pokai.shorturl.cache.ShortUrlCache;
import com.pokai.shorturl.config.ShortUrlProperties;
import com.pokai.shorturl.dto.CreateShortUrlRequest;
import com.pokai.shorturl.dto.ShortUrlResponse;
import com.pokai.shorturl.entity.UrlMapping;
import com.pokai.shorturl.exception.ShortUrlException;
import com.pokai.shorturl.repository.UrlMappingRepository;
import com.pokai.shorturl.util.Base62;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private final UrlMappingRepository repository;
    private final ShortUrlCache cache;
    private final ShortUrlProperties properties;

    /**
     * 建立短網址。
     * <p>
     * 沒指定自訂短碼時走「id → Base62」：先存一筆拿到自增 id，再把 id 編碼成短碼寫回。
     * 這樣短碼天生唯一，不需要防碰撞的重試迴圈。
     * 因為 short_code 是 NOT NULL + UNIQUE，第一次存檔要先塞一個暫時值。
     */
    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request) {
        String alias = request.getCustomAlias();

        if (alias != null && !alias.isBlank()) {
            if (repository.existsByShortCode(alias)) {
                throw ShortUrlException.aliasTaken(alias);
            }
            UrlMapping mapping = repository.save(UrlMapping.builder()
                    .shortCode(alias)
                    .originalUrl(request.getOriginalUrl())
                    .customAlias(true)
                    .expireAt(request.getExpireAt())
                    .build());
            return warmCacheAndRespond(mapping);
        }

        UrlMapping mapping = repository.saveAndFlush(UrlMapping.builder()
                .shortCode(temporaryCode())
                .originalUrl(request.getOriginalUrl())
                .customAlias(false)
                .expireAt(request.getExpireAt())
                .build());

        mapping.setShortCode(Base62.encode(mapping.getId(), properties.getCodeLength()));
        return warmCacheAndRespond(repository.save(mapping));
    }

    /**
     * 短碼換回原始網址，順便累加點擊數。
     * <p>
     * 走 cache-aside：先問快取，沒有才查 DB 並回填。
     * 命中快取時仍會下一句 UPDATE 累加點擊數，但省掉了 SELECT；
     * 之後要進一步減少 DB 壓力，可以把點擊數改成先累加在 Redis、再批次回寫。
     */
    @Transactional
    public String resolveAndCount(String shortCode) {
        ShortUrlCache.Lookup lookup = cache.lookup(shortCode);

        if (lookup.status() == ShortUrlCache.Status.KNOWN_MISSING) {
            throw ShortUrlException.notFound(shortCode);
        }
        if (lookup.status() == ShortUrlCache.Status.HIT) {
            repository.incrementClickCount(shortCode);
            return lookup.originalUrl();
        }

        UrlMapping mapping = repository.findByShortCode(shortCode).orElse(null);
        if (mapping == null) {
            // 記住這個短碼不存在，擋掉重複亂猜造成的快取穿透
            cache.putMissing(shortCode);
            throw ShortUrlException.notFound(shortCode);
        }

        if (isExpired(mapping)) {
            cache.evict(shortCode);
            throw ShortUrlException.gone(shortCode);
        }

        cache.put(shortCode, mapping.getOriginalUrl(), mapping.getExpireAt());
        repository.incrementClickCount(shortCode);
        return mapping.getOriginalUrl();
    }

    /**
     * 查短碼資訊（不累加點擊數），給儀表板用。
     * 這裡不走快取：點擊數要即時，而快取只存 originalUrl。
     */
    @Transactional(readOnly = true)
    public ShortUrlResponse getInfo(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(mapping -> ShortUrlResponse.of(mapping, properties.getBaseUrl()))
                .orElseThrow(() -> ShortUrlException.notFound(shortCode));
    }

    private boolean isExpired(UrlMapping mapping) {
        return mapping.getExpireAt() != null && mapping.getExpireAt().isBefore(LocalDateTime.now());
    }

    /**
     * 建立後先把資料放進快取，第一次點擊就不用查 DB。
     * 注意這是在交易提交前寫入的：萬一交易之後回滾，快取會留下一筆不存在的短碼，
     * 但它有 TTL 會自己過期，且點擊累加會影響 0 筆，影響有限。
     * 之後若要更嚴謹，可改成掛在 afterCommit 再寫。
     */
    private ShortUrlResponse warmCacheAndRespond(UrlMapping mapping) {
        cache.put(mapping.getShortCode(), mapping.getOriginalUrl(), mapping.getExpireAt());
        return ShortUrlResponse.of(mapping, properties.getBaseUrl());
    }

    /** 第一次存檔用的佔位短碼，長度控制在 16 以內以符合欄位限制 */
    private String temporaryCode() {
        return "tmp" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }
}
