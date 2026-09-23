# Short URL Service

[![CI](https://github.com/select1280/shortUrl/actions/workflows/ci.yml/badge.svg)](https://github.com/select1280/shortUrl/actions/workflows/ci.yml)

短網址服務 - 個人技術作品集專案，練習系統設計、快取應用與後端工程實務。

> 開發中，此 README 會隨功能進度持續更新。

## 目前進度

- [x] 專案骨架與資料庫 schema
- [x] 短網址產生邏輯（Base62 編碼）
- [x] 導向與點擊計數（click_count 累加）
- [x] Redis 快取（導向讀路徑）+ 防穿透 + 斷路器
- [x] 自訂短碼與有效期限
- [x] 點擊明細 click_log（非同步寫入）+ 統計 API（每日趨勢、熱門排行）
- [x] 測試：63 個，含以 Testcontainers 跑真實 MySQL 的查詢測試
- [x] Dockerfile + GitHub Actions CI
- [x] OpenAPI 文件 / Swagger UI
- [ ] 前端儀表板（Angular）

## 技術棧

- Java 17 / Spring Boot 3.2
- MySQL 8
- Redis 7
- Docker Compose

## 短碼產生策略

沒有指定自訂短碼時，走 **「DB 自增 id → Base62」**：

1. 先寫入一筆資料（`short_code` 暫時填佔位值）取得自增 id
2. 把 id 用 Base62 編碼，不足 `app.short-url.code-length` 時左補 `0`
3. 回寫 `short_code`

好處是短碼天生唯一，不需要「產生 → 檢查碰撞 → 重試」的迴圈。
缺點是短碼可被猜測、且會洩漏「總共建立過幾筆」，之後可以再加上 id 混淆或改成隨機碼。

## API

| Method | Path | 說明 |
| --- | --- | --- |
| POST | `/api/urls` | 建立短網址 |
| GET | `/api/urls/{shortCode}` | 查詢短碼資訊（不累加點擊數） |
| GET | `/{shortCode}` | 302 導向原始網址，並累加點擊數 |
| GET | `/api/urls/{shortCode}/stats/daily?from=&to=` | 單一短碼每日點擊數（沒點擊的日子補 0） |
| GET | `/api/stats/top?from=&to=&limit=` | 期間內點擊最多的短碼 |

建立短網址：

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/very/long/path"}'
```

回應：

```json
{
  "shortCode": "000001",
  "shortUrl": "http://localhost:8080/000001",
  "originalUrl": "https://example.com/some/very/long/path",
  "expireAt": null,
  "createdAt": "2026-09-08T10:00:00",
  "clickCount": 0
}
```

可選欄位：`customAlias`（4~16 碼英數字）、`expireAt`（ISO-8601，例如 `2026-12-31T23:59:59`）。

錯誤回應：短碼不存在 404、已過期 410、自訂短碼重複 409、參數驗證失敗 400。

## Redis 快取策略

導向（`GET /{shortCode}`）是讀多寫少的熱路徑，因此用 **cache-aside** 擋在 DB 前面：

```
查 Redis ──命中──> 直接導向
   │
  未命中
   │
   └─> 查 DB ──有資料──> 回填 Redis ──> 導向
              └─查無──> 寫入「不存在」標記 ──> 404
```

- **Key**：`shorturl:code:{shortCode}`，value 直接存原始網址
- **防快取穿透**：查無的短碼也會被快取（預設 60 秒）。否則有人拿亂數短碼狂打，每一發都會穿到 DB
- **TTL 對齊有效期限**：快取時間取 `min(設定 TTL, 距離 expireAt 的秒數)`，避免短碼過期後快取仍把人導去失效連結
- **降級**：所有 Redis 例外都在快取層被吃掉，退回查 DB。Redis 掛掉服務只是變慢，不會壞掉
- **斷路器**：光是吃掉例外還不夠 —— Redis 掛掉時每個請求都得各自等 timeout（實測每次約 1 秒），服務會慢到跟掛了差不多。因此失敗一次就開啟斷路器，接下來 30 秒直接跳過 Redis；時間到後放一個請求去試，成功就關閉。實測開啟後請求從 1 秒降到約 30 毫秒，log 也從每個請求一段 stack trace 變成只印一行
- **點擊數不進快取**：`click_count` 仍以 SQL 累加，確保統計正確。之後可再改成 Redis 累加、批次回寫

相關設定（`app.short-url.cache.*`）：`enabled`、`ttl`（預設 `1h`）、`null-ttl`（預設 `60s`）、`circuit-open-duration`（預設 `30s`）。

## API 文件

應用程式啟動後：

- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

文件由 Controller 上的註解自動產生，`OpenApiDocsTest` 會驗證每支 API 都出現在產出的文件裡 —— 新增 API 卻忘了寫註解時測試會失敗。

**prod profile 預設關閉文件**（回 404）。API 文件等於把所有端點、參數與錯誤碼攤開給任何人看，需要時再用 `SWAGGER_ENABLED=true` 個別打開。

## 點擊統計

每次導向成功都會寫一筆 `click_log`（時間、IP、User-Agent、Referer）：

- **非同步寫入**：導向是熱路徑，INSERT 丟到專用的背景執行緒池（`click-log-*`），導向本身只負責回 302
- **先複製再非同步**：請求的欄位在請求執行緒上先複製成 `ClickEvent`；回應送出後 Tomcat 會回收 request 物件，背景執行緒不能再讀它
- **佇列有上限，滿了就丟**：DB 變慢時寧可少記幾筆統計，也不要拖慢所有人的導向
- **存短碼不存 id**：導向命中快取時手上只有短碼，關聯 id 就得多查一次 DB
- **真實 IP**：prod 設 `server.forward-headers-strategy=native`，只信任內網代理送來的 `X-Forwarded-For`，不自己解析可被偽造的標頭

統計 API 的日期是 ISO 格式（`2026-09-23`），`from` / `to` 都含當天，不帶時預設最近 7 天，單次最多 90 天。
查詢一律用半開區間 `clicked_at >= from AND clicked_at < to+1`，EXPLAIN 實測能用到 `(short_code, clicked_at)` 索引的兩個欄位。

```bash
curl http://localhost:8080/api/urls/000001/stats/daily?from=2026-09-17&to=2026-09-23
```

```json
{
  "shortCode": "000001",
  "from": "2026-09-17",
  "to": "2026-09-23",
  "totalClicks": 6,
  "days": [
    { "date": "2026-09-17", "clicks": 0 },
    { "date": "2026-09-18", "clicks": 2 },
    "..."
  ]
}
```

> `click_log` 的總數可能略少於 `url_mapping.click_count`：前者是非同步寫入、佇列滿時會丟棄，後者是同步累加。

## 測試

```bash
./mvnw test
```

- 單元測試用 Mockito，不需要任何外部服務
- `ClickLogRepositoryTest` 用 Testcontainers 啟動真正的 MySQL 8 驗證查詢（日期轉換、GROUP BY 這類語法 H2 與 MySQL 行為不同）。需要 Docker；沒有 Docker 時會自動略過
- 注意：Docker Engine 29 以上需要 Testcontainers 1.21.4+（已在 `pom.xml` 覆寫），舊版會把 Docker 誤判成不存在而默默略過測試

## 設定檔

| 檔案 | 用途 |
| --- | --- |
| `application.yml` | 共用設定，並指定預設 profile 為 `dev` |
| `application-dev.yml` | 本機開發，對應 `docker-compose.yml` 起的 MySQL / Redis，`ddl-auto=update` |
| `application-prod.yml` | 正式環境，憑證全部由環境變數注入，`ddl-auto=validate` |

prod 需要的環境變數：`DB_HOST`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`SHORT_URL_BASE_URL`
（`DB_PORT`、`DB_NAME`、`REDIS_PORT`、`SERVER_PORT` 有預設值，可不給）。

`DB_USERNAME` / `DB_PASSWORD` 刻意不設預設值 —— 沒給就啟動失敗，避免誤用開發帳密上線。

## 本機啟動

```bash
# 1. 啟動資料庫與快取服務
docker compose up -d

# 2. 啟動應用程式（預設就是 dev profile）
./mvnw spring-boot:run
```

服務預設跑在 http://localhost:8080

以 prod profile 啟動：

```bash
java -jar target/short-url-service-0.1.0.jar --spring.profiles.active=prod
```

## 用容器跑整套

```bash
docker compose --profile full up -d --build
```

應用程式會等 MySQL 和 Redis 通過 healthcheck 才啟動，不是容器一起來就衝過去連。

映像檔的幾個設計：

- **多階段建置**：執行階段只帶 JRE，不含 JDK 與 Maven
- **分層 jar**：依賴和應用程式碼拆成不同 layer，改程式碼時不用重建依賴那層
- **非 root 執行**
- **`TZ=Asia/Taipei`**：容器預設 UTC，不設的話台灣時間 08:00 前的點擊會被算到前一天
- **`MaxRAMPercentage`**：讓 JVM 依容器的記憶體限制調整堆積大小，而不是看主機總記憶體

## CI

每次 push 和 PR 都會跑 [GitHub Actions](.github/workflows/ci.yml)：測試（含 Testcontainers 的真實 MySQL）與映像檔建置。

有一個步驟會**把被略過的測試當成失敗**。因為 Testcontainers 連不上 Docker 時，會把測試標成 skipped 而不是 failed，建置仍然顯示綠燈 —— 本機就踩過一次，4 個資料庫測試整整被略過還顯示通過。
