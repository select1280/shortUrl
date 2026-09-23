package com.pokai.shorturl.dto;

/** 熱門排行的一筆。clicks 用 Long 是為了配合 JPQL count() 的回傳型別 */
public record TopShortUrl(String shortCode, String originalUrl, Long clicks) {
}
