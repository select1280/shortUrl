package com.pokai.shorturl.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shortUrlOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Short URL Service API")
                .version("0.1.0")
                .description("""
                        短網址服務。

                        - 短碼由資料庫自增 id 以 Base62 編碼產生，也可以自訂
                        - 導向走 Redis 快取，並非同步記錄每一次點擊
                        - 統計 API 的日期為 ISO 格式（2026-09-23），from / to 皆含當天，
                          不帶參數時預設最近 7 天，單次查詢上限 90 天
                        """)
                .license(new License().name("MIT")));
    }
}
