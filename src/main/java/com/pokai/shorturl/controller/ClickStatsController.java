package com.pokai.shorturl.controller;

import com.pokai.shorturl.dto.DailyStatsResponse;
import com.pokai.shorturl.dto.TopStatsResponse;
import com.pokai.shorturl.service.ClickStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "點擊統計", description = "每日趨勢與熱門排行；資料來源是每次導向寫入的點擊明細")
public class ClickStatsController {

    private final ClickStatsService statsService;

    @GetMapping("/api/urls/{shortCode}/stats/daily")
    @Operation(summary = "單一短碼的每日點擊趨勢",
            description = "期間內沒有點擊的日子會補 0，前端畫折線圖才不會跳過那幾天")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功"),
            @ApiResponse(responseCode = "400", description = "日期格式錯誤、from 晚於 to，或期間超過 90 天", content = @Content),
            @ApiResponse(responseCode = "404", description = "短碼不存在", content = @Content)
    })
    public DailyStatsResponse daily(
            @Parameter(description = "短碼", example = "000001") @PathVariable String shortCode,
            @Parameter(description = "起始日期（含當天），預設為 to 往前推 7 天", example = "2026-09-17")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "結束日期（含當天），預設為今天", example = "2026-09-23")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.dailyClicks(shortCode, from, to);
    }

    @GetMapping("/api/stats/top")
    @Operation(summary = "期間內點擊數最多的短碼", description = "一併回傳原始網址")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功"),
            @ApiResponse(responseCode = "400", description = "日期格式錯誤、期間超過 90 天，或 limit 不在 1~100", content = @Content)
    })
    public TopStatsResponse top(
            @Parameter(description = "起始日期（含當天）", example = "2026-09-17")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "結束日期（含當天）", example = "2026-09-23")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "回傳筆數，1~100，預設 10", example = "10")
            @RequestParam(required = false) Integer limit) {
        return statsService.topShortUrls(from, to, limit);
    }
}
