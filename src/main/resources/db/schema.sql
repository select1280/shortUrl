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

-- click_log 留待「點擊統計」步驟再建立，先不在這個階段加入
