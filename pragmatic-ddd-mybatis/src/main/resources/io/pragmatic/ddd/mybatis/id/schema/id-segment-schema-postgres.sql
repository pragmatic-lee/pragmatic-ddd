-- id_segment 建表脚本（PostgreSQL 方言，ID 号段分配表）。
-- 与 id-segment-schema-mysql.sql 逻辑等价：DATETIME → TIMESTAMP、去掉 ENGINE/CHARSET、
-- MySQL 的 ON UPDATE CURRENT_TIMESTAMP 自动 bump 在 PG 不存在，
-- updated_at 由 IdSegmentMapper 的 incrementMax 语句显式赋值。
-- 脚本幂等（IF NOT EXISTS），可重复执行。

CREATE TABLE IF NOT EXISTS id_segment (
    biz_key         VARCHAR(64)   NOT NULL,
    current_max_id  BIGINT        NOT NULL,
    step            INT           NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    remark          VARCHAR(128)  DEFAULT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_key)
);

COMMENT ON TABLE id_segment IS 'ID 号段分配表';
COMMENT ON COLUMN id_segment.step IS '号段步长：每次取号分配的 ID 数量';
