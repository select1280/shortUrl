package com.pokai.shorturl.service;

import com.pokai.shorturl.dto.DailyClicks;
import com.pokai.shorturl.dto.DailyStatsResponse;
import com.pokai.shorturl.dto.TopShortUrl;
import com.pokai.shorturl.dto.TopStatsResponse;
import com.pokai.shorturl.exception.ShortUrlException;
import com.pokai.shorturl.repository.ClickLogRepository;
import com.pokai.shorturl.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickStatsServiceTest {

    private static final String CODE = "000001";
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    /** 固定「今天」為 2026-09-23 */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @Mock
    private ClickLogRepository clickLogRepository;

    @Mock
    private UrlMappingRepository urlMappingRepository;

    private ClickStatsService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(15, 0).atZone(TAIPEI).toInstant(), TAIPEI);
        service = new ClickStatsService(clickLogRepository, urlMappingRepository, clock);
    }

    @Test
    @DisplayName("沒有點擊的日子補 0，並算出總數")
    void daily_fillsMissingDaysWithZero() {
        LocalDate from = LocalDate.of(2026, 9, 20);
        LocalDate to = LocalDate.of(2026, 9, 23);
        when(urlMappingRepository.existsByShortCode(CODE)).thenReturn(true);
        when(clickLogRepository.countDailyClicks(anyString(), any(), any())).thenReturn(List.of(
                new DailyClicks(LocalDate.of(2026, 9, 20), 5L),
                new DailyClicks(LocalDate.of(2026, 9, 22), 3L)));

        DailyStatsResponse result = service.dailyClicks(CODE, from, to);

        assertThat(result.days()).containsExactly(
                new DailyClicks(LocalDate.of(2026, 9, 20), 5L),
                new DailyClicks(LocalDate.of(2026, 9, 21), 0L),
                new DailyClicks(LocalDate.of(2026, 9, 22), 3L),
                new DailyClicks(LocalDate.of(2026, 9, 23), 0L));
        assertThat(result.totalClicks()).isEqualTo(8);
    }

    @Test
    @DisplayName("日期轉成半開區間 [from 00:00, to 隔天 00:00) 交給查詢")
    void daily_queriesHalfOpenRange() {
        LocalDate from = LocalDate.of(2026, 9, 20);
        LocalDate to = LocalDate.of(2026, 9, 23);
        when(urlMappingRepository.existsByShortCode(CODE)).thenReturn(true);

        service.dailyClicks(CODE, from, to);

        verify(clickLogRepository).countDailyClicks(
                CODE, from.atStartOfDay(), LocalDate.of(2026, 9, 24).atStartOfDay());
    }

    @Test
    @DisplayName("沒給期間時預設最近 7 天（含今天）")
    void daily_defaultsToLastSevenDays() {
        when(urlMappingRepository.existsByShortCode(CODE)).thenReturn(true);

        DailyStatsResponse result = service.dailyClicks(CODE, null, null);

        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(result.to()).isEqualTo(TODAY);
        assertThat(result.days()).hasSize(7);
    }

    @Test
    @DisplayName("短碼不存在時回 404，不查點擊明細")
    void daily_unknownCode_returnsNotFound() {
        when(urlMappingRepository.existsByShortCode(CODE)).thenReturn(false);

        assertThatThrownBy(() -> service.dailyClicks(CODE, null, null))
                .isInstanceOf(ShortUrlException.class)
                .extracting(ex -> ((ShortUrlException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(clickLogRepository);
    }

    @Test
    @DisplayName("from 晚於 to 時回 400")
    void daily_rejectsReversedRange() {
        when(urlMappingRepository.existsByShortCode(CODE)).thenReturn(true);

        assertThatThrownBy(() -> service.dailyClicks(CODE, TODAY, TODAY.minusDays(1)))
                .isInstanceOf(ShortUrlException.class)
                .extracting(ex -> ((ShortUrlException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("期間剛好 90 天可以，91 天回 400")
    void daily_enforcesMaxRange() {
        when(urlMappingRepository.existsByShortCode(CODE)).thenReturn(true);

        assertThat(service.dailyClicks(CODE, TODAY.minusDays(89), TODAY).days()).hasSize(90);
        assertThatThrownBy(() -> service.dailyClicks(CODE, TODAY.minusDays(90), TODAY))
                .isInstanceOf(ShortUrlException.class)
                .hasMessageContaining("90");
    }

    @Test
    @DisplayName("熱門排行沒給 limit 時預設 10 筆")
    void top_defaultsToTenItems() {
        List<TopShortUrl> items = List.of(new TopShortUrl(CODE, "https://example.com", 3L));
        when(clickLogRepository.findTopShortUrls(any(), any(), any())).thenReturn(items);

        TopStatsResponse result = service.topShortUrls(null, null, null);

        assertThat(result.items()).isEqualTo(items);
        verify(clickLogRepository).findTopShortUrls(
                LocalDate.of(2026, 9, 17).atStartOfDay(),
                LocalDate.of(2026, 9, 24).atStartOfDay(),
                PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("limit 超出 1 ~ 100 時回 400")
    void top_rejectsInvalidLimit() {
        assertThatThrownBy(() -> service.topShortUrls(null, null, 0))
                .isInstanceOf(ShortUrlException.class);
        assertThatThrownBy(() -> service.topShortUrls(null, null, 101))
                .isInstanceOf(ShortUrlException.class);
        verifyNoInteractions(clickLogRepository);
    }
}
