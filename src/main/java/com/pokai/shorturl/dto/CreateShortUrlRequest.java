package com.pokai.shorturl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 建立短網址的請求。
 * customAlias 與 expireAt 目前先留欄位，之後做「自訂短碼與有效期限」時會用到。
 */
@Getter
@Setter
public class CreateShortUrlRequest {

    @NotBlank(message = "originalUrl 不可為空")
    @Pattern(regexp = "^https?://.+", message = "originalUrl 必須是 http/https 開頭")
    @Size(max = 2048, message = "originalUrl 長度不可超過 2048")
    private String originalUrl;

    @Size(min = 4, max = 16, message = "自訂短碼長度需介於 4~16")
    @Pattern(regexp = "^[0-9A-Za-z]*$", message = "自訂短碼只能包含英數字")
    private String customAlias;

    private LocalDateTime expireAt;
}
