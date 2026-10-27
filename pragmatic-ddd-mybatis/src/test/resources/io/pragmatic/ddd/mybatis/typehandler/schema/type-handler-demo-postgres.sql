-- TypeHandler 真实库集成测试建表脚本（PostgreSQL 方言，人工执行）
-- 数据库需与 PostgresTestSupport 的连接配置一致（默认 pragmatic_ddd）
-- 与 type-handler-demo-mysql.sql 逻辑等价：AUTO_INCREMENT → BIGSERIAL、JSON → JSONB、DATETIME → TIMESTAMP

CREATE TABLE IF NOT EXISTS type_handler_demo (
    id              BIGSERIAL     PRIMARY KEY,
    biz_name        VARCHAR(64)   NOT NULL,
    -- 单列枚举：CODE 策略（存枚举 value，INT）
    status_code     INT           DEFAULT NULL,
    -- 单列枚举：NAME 策略（存枚举常量名，VARCHAR）
    status_name     VARCHAR(32)   DEFAULT NULL,
    -- JSON 值对象：VO 内嵌枚举，整列 JSON（PgJdbcJsonValue 写入 PGobject(type=jsonb)）
    profile_json    JSONB         DEFAULT NULL,
    -- 枚举的 JSON List：List<ColorEnum>，整列 JSON 数组
    colors_json     JSONB         DEFAULT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE type_handler_demo IS 'TypeHandler 真实库集成测试表';
