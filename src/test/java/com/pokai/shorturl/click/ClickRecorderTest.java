package com.pokai.shorturl.click;

import com.pokai.shorturl.entity.ClickLog;
import com.pokai.shorturl.repository.ClickLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 這裡直接呼叫 record()，不經過 Spring 代理，所以 @Async 不會生效 —— 測的是寫入邏輯本身。
 * 非同步是否真的在背景執行緒跑，靠實機驗證確認。
 */
@ExtendWith(MockitoExtension.class)
class ClickRecorderTest {

    private static final ClickEvent EVENT = new ClickEvent(
            "000001", LocalDateTime.of(2026, 9, 23, 10, 0), "203.0.113.7", "Mozilla/5.0", null);

    @Mock
    private ClickLogRepository repository;

    @InjectMocks
    private ClickRecorder recorder;

    @Test
    @DisplayName("把點擊事件存成一筆 ClickLog")
    void record_savesClickLog() {
        recorder.record(EVENT);

        ArgumentCaptor<ClickLog> saved = ArgumentCaptor.forClass(ClickLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getShortCode()).isEqualTo("000001");
        assertThat(saved.getValue().getIp()).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("DB 寫入失敗時吞掉例外，背景執行緒不能因為一筆統計而炸掉")
    void record_swallowsDatabaseFailure() {
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatCode(() -> recorder.record(EVENT)).doesNotThrowAnyException();
    }
}
