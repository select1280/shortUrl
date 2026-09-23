package com.pokai.shorturl.dto;

import java.time.LocalDate;
import java.util.List;

/** 一段期間內點擊數最多的短碼 */
public record TopStatsResponse(
        LocalDate from,
        LocalDate to,
        List<TopShortUrl> items
) {
}
