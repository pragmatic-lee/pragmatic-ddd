-- compensation_log 建表脚本（PostgreSQL 方言，WAL 补偿日志表）。
-- 与 compensation-log-schema-mysql.sql 逻辑等价：LONGTEXT → text、DATETIME(3) → TIMESTAMP(3)、
-- 内联索引拆为独立 CREATE INDEX、去掉 ENGINE/CHARSET。
-- 脚本幂等（IF NOT EXISTS），可重复执行。

CREATE TABLE IF NOT EXISTS compensation_log (
    action_key   VARCHAR(255)  NOT NULL,
    handler      VARCHAR(255)  NOT NULL,
    payload      TEXT,
    status       VARCHAR(20)   NOT NULL,
    attempts     INT           NOT NULL DEFAULT 0,
    claim_token  VARCHAR(64),
    claimed_at   TIMESTAMP(3),
    last_error   VARCHAR(2000),
    created_at   TIMESTAMP(3)  NOT NULL,
    updated_at   TIMESTAMP(3)  NOT NULL,
    PRIMARY KEY (action_key)
);

CREATE INDEX IF NOT EXISTS idx_compensation_status_updated ON compensation_log (status, updated_at);

COMMENT ON TABLE compensation_log IS '补偿日志表（WAL）：PENDING/EXECUTED/COMPENSATING/COMPENSATED/CONFIRMED/FAILED';
