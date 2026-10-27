-- outbox_message 建表脚本（PostgreSQL 方言）。
-- 与 outbox-schema-mysql.sql 逻辑等价：LONGTEXT → text、DATETIME(3) → TIMESTAMP(3)、
-- 内联索引拆为独立 CREATE INDEX、去掉 ENGINE/CHARSET。
-- 脚本幂等（IF NOT EXISTS），可重复执行。
-- payload 采用 text 类型：框架不做 JSON 路径查询，text 与 MySQL LONGTEXT 语义等价、零驱动坑；
-- 若业务确需 jsonb，可自行改为 jsonb 并在对应参数上加 ::jsonb 显式转换。

CREATE TABLE IF NOT EXISTS outbox_message (
    id             VARCHAR(36)   NOT NULL,
    aggregate_id   VARCHAR(128)  NOT NULL,
    aggregate_type VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(255)  NOT NULL,
    entity_id      VARCHAR(128),
    payload        TEXT          NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    queue          INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(3)  NOT NULL,
    claimed_at     TIMESTAMP(3),
    sent_at        TIMESTAMP(3),
    last_error     VARCHAR(2000),
    claim_token    VARCHAR(36),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_message (status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_claim_token ON outbox_message (claim_token);

COMMENT ON TABLE outbox_message IS '事务性发件箱表（Outbox）';
COMMENT ON COLUMN outbox_message.status IS '状态：PENDING/PROCESSING/SENT/FAILED';
