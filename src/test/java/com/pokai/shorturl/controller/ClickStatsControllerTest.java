package com.pokai.shorturl.controller;

import com.pokai.shorturl.dto.DailyClicks;
import com.pokai.shorturl.dto.DailyStatsResponse;
import com.pokai.shorturl.dto.TopShortUrl;
import com.pokai.shorturl.dto.TopStatsResponse;
import com.pokai.shorturl.exception.ShortUrlException;
import com.pokai.shorturl.service.ClickStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClickStatsController.class)
class ClickStatsControllerTest {

    private static final String CODE = "000001";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 22);
    private static final LocalDate TO = LocalDate.of(2026, 9, 23);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClickStatsService statsService;

    @Test
    @DisplayName("每日趨勢：日期參數正確綁定，回傳 ISO 日期格式")
    void daily_bindsDatesAndReturnsSeries() throws Exception {
        when(statsService.dailyClicks(CODE, FROM, TO)).thenReturn(new DailyStatsResponse(
                CODE, FROM, TO, 4, List.of(new DailyClicks(FROM, 4L), new DailyClicks(TO, 0L))));

        mockMvc.perform(get("/api/urls/{code}/stats/daily", CODE)
                        .param("from", "2026-09-22")
                        .param("to", "2026-09-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(4))
                .andExpect(jsonPath("$.days[0].date").value("2026-09-22"))
                .andExpect(jsonPath("$.days[0].clicks").value(4))
                .andExpect(jsonPath("$.days[1].clicks").value(0));
    }

    @Test
    @DisplayName("不帶日期參數時傳 null，由 service 決定預設期間")
    void daily_passesNullWhenDatesOmitted() throws Exception {
        when(statsService.dailyClicks(anyString(), any(), any()))
                .thenReturn(new DailyStatsResponse(CODE, FROM, TO, 0, List.of()));

        mockMvc.perform(get("/api/urls/{code}/stats/daily", CODE))
                .andExpect(status().isOk());

        verify(statsService).dailyClicks(CODE, null, null);
    }

    @Test
    @DisplayName("日期格式錯誤時回 400，且錯誤格式跟其他 API 一致")
    void daily_rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/urls/{code}/stats/daily", CODE)
                        .param("from", "2026/09/22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("參數 from 格式錯誤: 2026/09/22"));
    }

    @Test
    @DisplayName("短碼不存在時回 404")
    void daily_unknownCode_returnsNotFound() throws Exception {
        when(statsService.dailyClicks(anyString(), any(), any())).thenThrow(ShortUrlException.notFound(CODE));

        mockMvc.perform(get("/api/urls/{code}/stats/daily", CODE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("熱門排行：回傳排行項目與原始網址")
    void top_returnsItems() throws Exception {
        when(statsService.topShortUrls(isNull(), isNull(), any())).thenReturn(new TopStatsResponse(
                FROM, TO, List.of(new TopShortUrl(CODE, "https://example.com", 12L))));

        mockMvc.perform(get("/api/stats/top").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].shortCode").value(CODE))
                .andExpect(jsonPath("$.items[0].originalUrl").value("https://example.com"))
                .andExpect(jsonPath("$.items[0].clicks").value(12));

        verify(statsService).topShortUrls(null, null, 5);
    }

    @Test
    @DisplayName("limit 不是數字時回 400")
    void top_rejectsNonNumericLimit() throws Exception {
        mockMvc.perform(get("/api/stats/top").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
