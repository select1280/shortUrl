# Short URL Service

短網址服務 - 個人技術作品集專案，練習系統設計、快取應用與後端工程實務。

> 開發中，此 README 會隨功能進度持續更新。

## 目前進度

- [x] 專案骨架與資料庫 schema
- [x] 短網址產生邏輯（Base62 編碼）
- [x] 導向與點擊統計（click_count 累加；點擊明細 click_log 尚未做）
- [x] Redis 快取（導向讀路徑）
- [x] 自訂短碼與有效期限
- [ ] 單元測試（Base62 / cache / service 已覆蓋，controller 待補）
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
