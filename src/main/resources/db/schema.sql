-- 短網址服務資料庫 schema
-- 目前開發階段由 Hibernate ddl-auto=update 自動產生，此檔案作為文件與日後遷移基準保留。

CREATE TABLE IF NOT EXISTS url_mapping (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code    VARCHAR(16)   NOT NULL,
    original_url  VARCHAR(2048) NOT NULL,
    custom_alias  BOOLEAN       NOT NULL DEFAULT FALSE,
    expire_at     DATETIME      NULL,
    created_at    DATETIME      NOT NULL,
    click_count   BIGINT        NOT NULL DEFAULT 0,
    UNIQUE KEY idx_short_code (short_code)
);

-- 每一次導向的明細，用來做每日趨勢與熱門排行。
-- 存 short_code 而不是 url_mapping.id：導向命中快取時手上只有短碼，
-- 若要存 id 就得多查一次 DB，快取就白做了。
CREATE TABLE IF NOT EXISTS click_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code   VARCHAR(16)   NOT NULL,
    clicked_at   DATETIME      NOT NULL,
    ip           VARCHAR(45)   NULL,      -- 45 字元容得下 IPv6
    user_agent   VARCHAR(512)  NULL,
    referer      VARCHAR(2048) NULL,
    -- 查單一短碼的每日趨勢：WHERE short_code = ? AND clicked_at BETWEEN ...
    KEY idx_click_code_time (short_code, clicked_at),
    -- 查某段時間的熱門排行：WHERE clicked_at BETWEEN ... GROUP BY short_code
    KEY idx_click_time (clicked_at)
);
