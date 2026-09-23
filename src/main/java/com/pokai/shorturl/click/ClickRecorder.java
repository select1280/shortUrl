package com.pokai.shorturl.click;

import com.pokai.shorturl.config.AsyncConfig;
import com.pokai.shorturl.repository.ClickLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 非同步寫入點擊明細。
 * 導向是熱路徑，多一次 INSERT 就多一次延遲；點擊明細晚幾毫秒進 DB 沒有人會在意，
 * 所以丟到背景執行緒做，導向本身只負責回 302。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClickRecorder {

    private final ClickLogRepository repository;

    @Async(AsyncConfig.CLICK_LOG_EXECUTOR)
    public void record(ClickEvent event) {
        try {
            repository.save(event.toEntity());
            // dev 開 DEBUG 時看得到執行緒名稱，可以確認真的是在 click-log-* 背景執行緒寫入
            log.debug("已記錄點擊 shortCode={}", event.shortCode());
        } catch (RuntimeException ex) {
            // 背景執行緒的例外不會傳回給呼叫端，不接住的話只會默默消失
            log.warn("寫入點擊明細失敗，略過。shortCode={}, 原因: {}", event.shortCode(), ex.getMessage());
        }
    }
}
