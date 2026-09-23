package com.pokai.shorturl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 把「現在時間」做成可注入的 Bean。
 * 程式裡不直接呼叫 Instant.now()，測試時才能換成假的時鐘，驗證「30 秒後」這種邏輯不用真的等 30 秒。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
