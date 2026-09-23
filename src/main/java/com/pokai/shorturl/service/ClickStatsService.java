package com.pokai.shorturl.service;

import com.pokai.shorturl.dto.DailyClicks;
import com.pokai.shorturl.dto.DailyStatsResponse;
import com.pokai.shorturl.dto.TopStatsResponse;
import com.pokai.shorturl.exception.ShortUrlException;
import com.pokai.shorturl.repository.ClickLogRepository;
import com.pokai.shorturl.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 點擊統計。資料來源是 click_log 明細，日期以伺服器時區（Asia/Taipei）切分。
 * <p>
 * 注意：這裡的總數可能略少於 url_mapping.click_count ——
 * click_log 是非同步寫入，佇列滿時會丟棄，而 click_count 是同步累加。
 */
@Service
@RequiredArgsConstructor
public class ClickStatsService {

    /** 沒指定期間時預設看最近 7 天（含今天） */
    static final int DEFAULT_DAYS = 7;

    /** 單次查詢最多 90 天，避免一支 API 掃過整張表 */
    static final int MAX_DAYS = 90;

    static final int DEFAULT_TOP_LIMIT = 10;
    static final int MAX_TOP_LIMIT = 100;

    private final ClickLogRepository clickLogRepository;
    private final UrlMappingRepository urlMappingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DailyStatsResponse dailyClicks(String shortCode, LocalDate from, LocalDate to) {
        if (!urlMappingRepository.existsByShortCode(shortCode)) {
            throw ShortUrlException.notFound(shortCode);
        }
        DateRange range = resolveRange(from, to);

        List<DailyClicks> rows = clickLogRepository.countDailyClicks(
                shortCode, range.from().atStartOfDay(), range.to().plusDays(1).atStartOfDay());

        List<DailyClicks> days = fillMissingDays(rows, range);
        long total = days.stream().mapToLong(DailyClicks::clicks).sum();
        return new DailyStatsResponse(shortCode, range.from(), range.to(), total, days);
    }

    @Transactional(readOnly = true)
    public TopStatsResponse topShortUrls(LocalDate from, LocalDate to, Integer limit) {
        DateRange range = resolveRange(from, to);
        int size = limit == null ? DEFAULT_TOP_LIMIT : limit;
        if (size < 1 || size > MAX_TOP_LIMIT) {
            throw ShortUrlException.badRequest("limit 必須介於 1 ~ " + MAX_TOP_LIMIT);
        }

        return new TopStatsResponse(range.from(), range.to(), clickLogRepository.findTopShortUrls(
                range.from().atStartOfDay(), range.to().plusDays(1).atStartOfDay(), PageRequest.of(0, size)));
    }

    /**
     * 補上缺席的日期：DB 只會回傳有點擊的日子，
     * 但前端畫折線圖需要連續的每一天，否則線會直接跨過沒資料的日子，看起來像每天都有點擊。
     */
    private List<DailyClicks> fillMissingDays(List<DailyClicks> rows, DateRange range) {
        Map<LocalDate, Long> clicksByDate = rows.stream()
                .collect(Collectors.toMap(DailyClicks::date, DailyClicks::clicks));

        List<DailyClicks> days = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            days.add(new DailyClicks(day, clicksByDate.getOrDefault(day, 0L)));
        }
        return days;
    }

    /** 補預設值並檢查期間。from / to 都是含頭含尾的日期 */
    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);

        if (start.isAfter(end)) {
            throw ShortUrlException.badRequest("from 不可晚於 to");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ShortUrlException.badRequest("查詢期間最多 " + MAX_DAYS + " 天");
        }
        return new DateRange(start, end);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
