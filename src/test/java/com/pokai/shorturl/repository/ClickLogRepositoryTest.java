package com.pokai.shorturl.repository;

import com.pokai.shorturl.dto.DailyClicks;
import com.pokai.shorturl.dto.TopShortUrl;
import com.pokai.shorturl.entity.ClickLog;
import com.pokai.shorturl.entity.UrlMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用 Testcontainers 起一個真的 MySQL 8 跑查詢。
 * 日期轉換、GROUP BY 這些語法 H2 跟 MySQL 行為不一樣，用 H2 測過不代表上線會對。
 * 沒有 Docker 的環境會自動略過，不會讓整個建置失敗。
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClickLogRepositoryTest {

    /**
     * 刻意不用 @Container：@Container 會在這個測試類別結束時就關掉容器，
     * 但 Spring 會把 context 快取到 JVM 結束才關，連線池關閉時資料庫已經不在，JVM 會卡 30 秒才退出。
     * 改成手動啟動、整個 JVM 共用一個容器，測試結束後由 Testcontainers 的 Ryuk 自動清掉。
     */
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    static {
        mysql.start();
    }

    private static final LocalDate DAY1 = LocalDate.of(2026, 9, 20);
    private static final LocalDate DAY2 = LocalDate.of(2026, 9, 21);
    private static final LocalDate DAY3 = LocalDate.of(2026, 9, 22);

    @Autowired
    private ClickLogRepository clickLogRepository;

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @BeforeEach
    void setUp() {
        clickLogRepository.deleteAll();
        urlMappingRepository.deleteAll();
        saveMapping("aaaa", "https://a.example.com");
        saveMapping("bbbb", "https://b.example.com");
        saveMapping("cccc", "https://c.example.com");
    }

    @Test
    @DisplayName("每日點擊數依日期分組，且只算指定的短碼")
    void countDailyClicks_groupsByDay() {
        click("aaaa", DAY1.atTime(9, 0));
        click("aaaa", DAY1.atTime(23, 59, 59));
        click("aaaa", DAY3.atTime(12, 0));
        click("bbbb", DAY1.atTime(10, 0));   // 別的短碼，不應該算進來

        List<DailyClicks> result = clickLogRepository.countDailyClicks(
                "aaaa", DAY1.atStartOfDay(), DAY3.plusDays(1).atStartOfDay());

        // 沒有點擊的 DAY2 不會出現，補 0 是 service 的工作
        assertThat(result).containsExactly(
                new DailyClicks(DAY1, 2L),
                new DailyClicks(DAY3, 1L));
    }

    @Test
    @DisplayName("期間是半開區間：含起點當下，不含終點當下")
    void countDailyClicks_usesHalfOpenRange() {
        click("aaaa", DAY2.atStartOfDay());                      // 剛好在起點 → 算
        click("aaaa", DAY2.atTime(23, 59, 59, 999_000_000));     // 當天最後一刻 → 算
        click("aaaa", DAY3.atStartOfDay());                      // 剛好在終點 → 不算
        click("aaaa", DAY1.atTime(23, 59, 59));                  // 起點之前 → 不算

        List<DailyClicks> result = clickLogRepository.countDailyClicks(
                "aaaa", DAY2.atStartOfDay(), DAY3.atStartOfDay());

        assertThat(result).containsExactly(new DailyClicks(DAY2, 2L));
    }

    @Test
    @DisplayName("熱門排行依點擊數由多到少，並帶出原始網址")
    void findTopShortUrls_ordersByClicks() {
        click("bbbb", DAY1.atTime(9, 0));
        click("bbbb", DAY1.atTime(10, 0));
        click("bbbb", DAY2.atTime(10, 0));
        click("aaaa", DAY1.atTime(9, 0));
        click("cccc", DAY2.atTime(9, 0));
        click("cccc", DAY2.atTime(9, 30));

        List<TopShortUrl> result = clickLogRepository.findTopShortUrls(
                DAY1.atStartOfDay(), DAY3.atStartOfDay(), PageRequest.of(0, 10));

        assertThat(result).containsExactly(
                new TopShortUrl("bbbb", "https://b.example.com", 3L),
                new TopShortUrl("cccc", "https://c.example.com", 2L),
                new TopShortUrl("aaaa", "https://a.example.com", 1L));
    }

    @Test
    @DisplayName("熱門排行只算期間內的點擊，且遵守筆數上限")
    void findTopShortUrls_respectsRangeAndLimit() {
        click("aaaa", DAY1.atTime(9, 0));
        click("aaaa", DAY1.atTime(9, 1));
        click("aaaa", DAY1.atTime(9, 2));   // aaaa 全部在期間外
        click("bbbb", DAY2.atTime(9, 0));
        click("bbbb", DAY2.atTime(9, 1));
        click("cccc", DAY2.atTime(9, 0));

        List<TopShortUrl> result = clickLogRepository.findTopShortUrls(
                DAY2.atStartOfDay(), DAY3.atStartOfDay(), PageRequest.of(0, 1));

        assertThat(result).containsExactly(new TopShortUrl("bbbb", "https://b.example.com", 2L));
    }

    private void saveMapping(String code, String url) {
        urlMappingRepository.save(UrlMapping.builder()
                .shortCode(code)
                .originalUrl(url)
                .customAlias(true)
                .build());
    }

    private void click(String code, LocalDateTime at) {
        clickLogRepository.save(ClickLog.builder()
                .shortCode(code)
                .clickedAt(at)
                .ip("203.0.113.7")
                .build());
    }
}
