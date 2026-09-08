package com.pokai.shorturl.dto;

import com.pokai.shorturl.entity.UrlMapping;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ShortUrlResponse {

    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private Long clickCount;

    public static ShortUrlResponse of(UrlMapping mapping, String baseUrl) {
        return ShortUrlResponse.builder()
                .shortCode(mapping.getShortCode())
                .shortUrl(baseUrl + mapping.getShortCode())
                .originalUrl(mapping.getOriginalUrl())
                .expireAt(mapping.getExpireAt())
                .createdAt(mapping.getCreatedAt())
                .clickCount(mapping.getClickCount())
                .build();
    }
}
