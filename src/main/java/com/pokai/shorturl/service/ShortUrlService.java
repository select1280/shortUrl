package com.pokai.shorturl.service;

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
            return ShortUrlResponse.of(mapping, properties.getBaseUrl());
        }

        UrlMapping mapping = repository.saveAndFlush(UrlMapping.builder()
                .shortCode(temporaryCode())
                .originalUrl(request.getOriginalUrl())
                .customAlias(false)
                .expireAt(request.getExpireAt())
                .build());

        mapping.setShortCode(Base62.encode(mapping.getId(), properties.getCodeLength()));
        return ShortUrlResponse.of(repository.save(mapping), properties.getBaseUrl());
    }

    /**
     * 短碼換回原始網址，順便累加點擊數。
     */
    @Transactional
    public String resolveAndCount(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> ShortUrlException.notFound(shortCode));

        if (mapping.getExpireAt() != null && mapping.getExpireAt().isBefore(LocalDateTime.now())) {
            throw ShortUrlException.gone(shortCode);
        }

        repository.incrementClickCount(shortCode);
        return mapping.getOriginalUrl();
    }

    /**
     * 查短碼資訊（不累加點擊數），給儀表板用。
     */
    @Transactional(readOnly = true)
    public ShortUrlResponse getInfo(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(mapping -> ShortUrlResponse.of(mapping, properties.getBaseUrl()))
                .orElseThrow(() -> ShortUrlException.notFound(shortCode));
    }

    /** 第一次存檔用的佔位短碼，長度控制在 16 以內以符合欄位限制 */
    private String temporaryCode() {
        return "tmp" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }
}
