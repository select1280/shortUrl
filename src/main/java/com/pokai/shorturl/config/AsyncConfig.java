package com.pokai.shorturl.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    public static final String CLICK_LOG_EXECUTOR = "clickLogExecutor";

    /** 每丟掉這麼多筆才印一次 log，避免過載時 log 也跟著洗版 */
    private static final long DROP_LOG_INTERVAL = 1000;

    private final AtomicLong droppedClicks = new AtomicLong();

    /**
     * 點擊明細專用的執行緒池，跟其他背景工作隔開。
     * <p>
     * 佇列有上限：DB 變慢時佇列不會無限長大把記憶體吃光。
     * 佇列滿了就直接丟掉這筆點擊，而不是用 CallerRunsPolicy 讓請求執行緒自己寫 ——
     * 那樣會把 DB 的慢傳染給導向，正好違背非同步的初衷。少記幾筆統計，比所有人的導向都變慢好。
     */
    @Bean(name = CLICK_LOG_EXECUTOR)
    public ThreadPoolTaskExecutor clickLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("click-log-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            long dropped = droppedClicks.incrementAndGet();
            if (dropped == 1 || dropped % DROP_LOG_INTERVAL == 0) {
                log.warn("點擊明細佇列已滿，累計丟棄 {} 筆", dropped);
            }
        });
        // 關機時把佇列裡還沒寫的點擊寫完再走
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
