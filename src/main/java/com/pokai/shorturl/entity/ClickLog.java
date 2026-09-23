package com.pokai.shorturl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 單次導向的點擊明細。
 * 只寫不改，所以不開 Setter。
 * 存 shortCode 而不是 UrlMapping 的關聯：導向命中快取時手上只有短碼，關聯到 id 就得多查一次 DB。
 */
@Entity
@Table(name = "click_log", indexes = {
        @Index(name = "idx_click_code_time", columnList = "short_code, clicked_at"),
        @Index(name = "idx_click_time", columnList = "clicked_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickLog {

    public static final int USER_AGENT_MAX_LENGTH = 512;
    public static final int REFERER_MAX_LENGTH = 2048;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 16)
    private String shortCode;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    /** 45 字元容得下 IPv6 */
    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    @Column(name = "referer", length = REFERER_MAX_LENGTH)
    private String referer;
}
