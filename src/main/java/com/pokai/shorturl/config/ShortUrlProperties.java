package com.pokai.shorturl.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 對應 application.yml 的 app.short-url.*
 */
@Component
@ConfigurationProperties(prefix = "app.short-url")
@Getter
@Setter
public class ShortUrlProperties {

    /** 短網址前綴，結尾一律補上 '/'，組字串時才不用再判斷 */
    private String baseUrl = "http://localhost:8080/";

    /** 產生短碼的最小長度，不足時左補 '0' */
    private int codeLength = 6;

    public String getBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
}
