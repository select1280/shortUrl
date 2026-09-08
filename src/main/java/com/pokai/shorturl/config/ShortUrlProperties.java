package com.pokai.shorturl.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 對應 application.yml 的 app.short-url.*
 */
@Component
@ConfigurationProperties(prefix = "app.short-url")
@Getter
@Setter
public class ShortUrlProperties {

    /** 短網址前綴，結尾一律補上 '/'，組字串時才不用再判斷 */
    private String baseUrl = "http://localhost:8080/";

    /** 產生短碼的最小長度，不足時左補 '0' */
    private int codeLength = 6;

    private final Cache cache = new Cache();

    public String getBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Getter
    @Setter
    public static class Cache {

        /** 是否啟用 Redis 快取，測試或 Redis 未就緒時可關掉 */
        private boolean enabled = true;

        /** 短碼對應的快取存活時間 */
        private Duration ttl = Duration.ofHours(1);

        /**
         * 「查無此短碼」的快取存活時間。
         * 刻意設得很短：避免有人亂猜短碼把 DB 打爆（快取穿透），
         * 又不會讓之後才建立的同名短碼被卡太久。
         */
        private Duration nullTtl = Duration.ofSeconds(60);
    }
}
