package com.pokai.shorturl.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokai.shorturl.dto.ShortUrlResponse;
import com.pokai.shorturl.exception.ShortUrlException;
import com.pokai.shorturl.service.ShortUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 只載入 Web 層：Controller、參數驗證、GlobalExceptionHandler。
 * Service 是假的，所以不需要 MySQL 和 Redis，測試跑得快也不會因為環境而失敗。
 */
@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    private static final String CODE = "000001";
    private static final String URL = "https://example.com/a/long/path";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShortUrlService service;

    @Test
    @DisplayName("建立短網址回 201 與短網址資訊")
    void create_returnsCreated() throws Exception {
        when(service.create(any())).thenReturn(ShortUrlResponse.builder()
                .shortCode(CODE)
                .shortUrl("http://localhost:8080/" + CODE)
                .originalUrl(URL)
                .createdAt(LocalDateTime.now())
                .clickCount(0L)
                .build());

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalUrl", URL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(CODE))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/" + CODE))
                .andExpect(jsonPath("$.originalUrl").value(URL));
    }

    @Test
    @DisplayName("originalUrl 不是 http/https 開頭時回 400")
    void create_rejectsNonHttpUrl() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalUrl", "ftp://example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("originalUrl 空白時回 400")
    void create_rejectsBlankUrl() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("originalUrl", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("自訂短碼含非英數字時回 400")
    void create_rejectsInvalidAlias() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("originalUrl", URL, "customAlias", "my-link!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("導向時回 302 並帶上 Location")
    void redirect_returnsFound() throws Exception {
        when(service.resolveAndCount(CODE)).thenReturn(URL);

        mockMvc.perform(get("/" + CODE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", URL));
    }

    @Test
    @DisplayName("短碼不存在時回 404")
    void redirect_notFound() throws Exception {
        when(service.resolveAndCount(CODE)).thenThrow(ShortUrlException.notFound(CODE));

        mockMvc.perform(get("/" + CODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("短碼已過期時回 410")
    void redirect_expiredReturnsGone() throws Exception {
        when(service.resolveAndCount(CODE)).thenThrow(ShortUrlException.gone(CODE));

        mockMvc.perform(get("/" + CODE))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("自訂短碼重複時回 409")
    void create_duplicateAliasReturnsConflict() throws Exception {
        when(service.create(any())).thenThrow(ShortUrlException.aliasTaken("mylink"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("originalUrl", URL, "customAlias", "mylink"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("查詢短碼資訊回 200")
    void info_returnsOk() throws Exception {
        when(service.getInfo(CODE)).thenReturn(ShortUrlResponse.builder()
                .shortCode(CODE)
                .shortUrl("http://localhost:8080/" + CODE)
                .originalUrl(URL)
                .createdAt(LocalDateTime.now())
                .clickCount(42L)
                .build());

        mockMvc.perform(get("/api/urls/" + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(42));
    }

    @Test
    @DisplayName("短碼長度不符路由規則時不進 Controller")
    void redirect_ignoresPathsThatAreNotShortCodes() throws Exception {
        mockMvc.perform(get("/ab"))
                .andExpect(status().isNotFound());

        verify(service, never()).resolveAndCount(anyString());
    }
}
