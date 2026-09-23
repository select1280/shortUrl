package com.pokai.shorturl;

import com.pokai.shorturl.repository.ClickLogRepository;
import com.pokai.shorturl.repository.UrlMappingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 確認 OpenAPI 文件產得出來、而且每支 API 都在裡面。
 * 文件是照 Controller 的註解自動產生的，新增 API 卻忘了寫註解時這裡會抓到。
 * <p>
 * 資料庫相關的自動設定關掉，Repository 用假的，所以不需要 MySQL 和 Redis。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@TestPropertySource(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration")
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlMappingRepository urlMappingRepository;

    @MockBean
    private ClickLogRepository clickLogRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("OpenAPI 文件包含所有端點")
    void apiDocs_containsAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Short URL Service API"))
                .andExpect(jsonPath("$.paths['/api/urls'].post").exists())
                .andExpect(jsonPath("$.paths['/api/urls/{shortCode}'].get").exists())
                .andExpect(jsonPath("$.paths['/{shortCode}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/urls/{shortCode}/stats/daily'].get").exists())
                .andExpect(jsonPath("$.paths['/api/stats/top'].get").exists());
    }

    @Test
    @DisplayName("導向端點有記錄 302 / 404 / 410 三種回應")
    void apiDocs_documentsRedirectResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/{shortCode}'].get.responses.302").exists())
                .andExpect(jsonPath("$.paths['/{shortCode}'].get.responses.404").exists())
                .andExpect(jsonPath("$.paths['/{shortCode}'].get.responses.410").exists());
    }
}
