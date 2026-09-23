package com.pokai.shorturl.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 單一短碼的每日點擊趨勢。
 * days 會涵蓋 from ~ to 的每一天，沒有點擊的日子補 0，前端畫折線圖才不會斷線或跳天。
 */
public record DailyStatsResponse(
        String shortCode,
        LocalDate from,
        LocalDate to,
        long totalClicks,
        List<DailyClicks> days
) {
}
