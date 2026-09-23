package com.pokai.shorturl.dto;

import java.time.LocalDate;

/** 某一天的點擊數。clicks 用 Long 是為了配合 JPQL count() 的回傳型別 */
public record DailyClicks(LocalDate date, Long clicks) {
}
