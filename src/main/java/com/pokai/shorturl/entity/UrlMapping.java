package com.pokai.shorturl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 短網址對應紀錄。
 * short_code 對應到唯一一組 original_url，
 * click_count 為快速統計用的冗餘欄位，詳細點擊紀錄另見 ClickLog（後續步驟補上）。
 */
@Entity
@Table(name = "url_mapping", indexes = {
        @Index(name = "idx_short_code", columnList = "short_code", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 16)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
