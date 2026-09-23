package com.pokai.shorturl.controller;

import com.pokai.shorturl.dto.DailyStatsResponse;
import com.pokai.shorturl.dto.TopStatsResponse;
import com.pokai.shorturl.service.ClickStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 點擊統計 API。日期格式為 ISO-8601（2026-09-23），from / to 都含當天；
 * 不帶參數時預設最近 7 天。
 */
@RestController
@RequiredArgsConstructor
public class ClickStatsController {

    private final ClickStatsService statsService;

    /** 單一短碼的每日點擊趨勢，沒有點擊的日子補 0 */
    @GetMapping("/api/urls/{shortCode}/stats/daily")
    public DailyStatsResponse daily(
            @PathVariable String shortCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.dailyClicks(shortCode, from, to);
    }

    /** 期間內點擊數最多的短碼 */
    @GetMapping("/api/stats/top")
    public TopStatsResponse top(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return statsService.topShortUrls(from, to, limit);
    }
}
