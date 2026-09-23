package com.pokai.shorturl.click;

import com.pokai.shorturl.entity.ClickLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;

/**
 * 一次點擊的快照，在請求執行緒上建立，之後交給非同步執行緒寫入。
 * <p>
 * 為什麼不直接把 HttpServletRequest 丟給 @Async：回應送出後 Tomcat 會回收 request 物件，
 * 非同步執行緒再去讀它，拿到的可能是空值、甚至是下一個請求的資料。所以要先把需要的欄位複製出來。
 */
public record ClickEvent(
        String shortCode,
        LocalDateTime clickedAt,
        String ip,
        String userAgent,
        String referer
) {

    /**
     * IP 直接用 getRemoteAddr()：部署在反向代理後面時，由 server.forward-headers-strategy
     * 負責從 X-Forwarded-For 還原真實 IP，而且只信任內網代理。
     * 不自己讀 X-Forwarded-For，因為任何人都能在請求裡偽造這個標頭。
     */
    public static ClickEvent from(HttpServletRequest request, String shortCode, LocalDateTime clickedAt) {
        return new ClickEvent(
                shortCode,
                clickedAt,
                request.getRemoteAddr(),
                truncate(request.getHeader(HttpHeaders.USER_AGENT), ClickLog.USER_AGENT_MAX_LENGTH),
                truncate(request.getHeader(HttpHeaders.REFERER), ClickLog.REFERER_MAX_LENGTH)
        );
    }

    public ClickLog toEntity() {
        return ClickLog.builder()
                .shortCode(shortCode)
                .clickedAt(clickedAt)
                .ip(ip)
                .userAgent(userAgent)
                .referer(referer)
                .build();
    }

    /** 標頭長度由使用者控制，超過欄位長度會讓 INSERT 失敗，所以先截斷 */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
