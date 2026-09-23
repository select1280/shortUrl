package com.pokai.shorturl.click;

import com.pokai.shorturl.entity.ClickLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClickEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0);

    @Test
    @DisplayName("從請求複製出 IP、User-Agent、Referer")
    void from_copiesRequestFields() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Referer", "https://news.example.com/post/1");

        ClickEvent event = ClickEvent.from(request, "000001", NOW);

        assertThat(event.shortCode()).isEqualTo("000001");
        assertThat(event.clickedAt()).isEqualTo(NOW);
        assertThat(event.ip()).isEqualTo("203.0.113.7");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.referer()).isEqualTo("https://news.example.com/post/1");
    }

    @Test
    @DisplayName("沒有 User-Agent / Referer 標頭時存 null，不能出錯")
    void from_allowsMissingHeaders() {
        ClickEvent event = ClickEvent.from(new MockHttpServletRequest(), "000001", NOW);

        assertThat(event.userAgent()).isNull();
        assertThat(event.referer()).isNull();
    }

    @Test
    @DisplayName("超長標頭截斷到欄位長度，否則 INSERT 會失敗")
    void from_truncatesOversizedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "a".repeat(ClickLog.USER_AGENT_MAX_LENGTH + 100));
        request.addHeader("Referer", "b".repeat(ClickLog.REFERER_MAX_LENGTH + 100));

        ClickEvent event = ClickEvent.from(request, "000001", NOW);

        assertThat(event.userAgent()).hasSize(ClickLog.USER_AGENT_MAX_LENGTH);
        assertThat(event.referer()).hasSize(ClickLog.REFERER_MAX_LENGTH);
    }

    @Test
    @DisplayName("轉成 Entity 時欄位一一對應")
    void toEntity_mapsAllFields() {
        ClickEvent event = new ClickEvent("000001", NOW, "203.0.113.7", "Mozilla/5.0", "https://ref.example.com");

        ClickLog log = event.toEntity();

        assertThat(log.getId()).isNull();
        assertThat(log.getShortCode()).isEqualTo("000001");
        assertThat(log.getClickedAt()).isEqualTo(NOW);
        assertThat(log.getIp()).isEqualTo("203.0.113.7");
        assertThat(log.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(log.getReferer()).isEqualTo("https://ref.example.com");
    }
}
