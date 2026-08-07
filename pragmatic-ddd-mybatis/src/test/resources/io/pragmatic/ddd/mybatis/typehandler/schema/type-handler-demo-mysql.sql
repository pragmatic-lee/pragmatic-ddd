-- TypeHandler 真实库集成测试建表脚本（人工执行）
-- 数据库需与 MysqlTestSupport 的连接配置一致（默认 pragmatic_ddb）

CREATE TABLE IF NOT EXISTS type_handler_demo (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    biz_name        VARCHAR(64)   NOT NULL,
    -- 单列枚举：CODE 策略（存枚举 value，INT）
    status_code     INT           DEFAULT NULL,
    -- 单列枚举：NAME 策略（存枚举常量名，VARCHAR）
    status_name     VARCHAR(32)   DEFAULT NULL,
    -- JSON 值对象：VO 内嵌枚举，整列 JSON
    profile_json    JSON          DEFAULT NULL,
    -- 枚举的 JSON List：List<ColorEnum>，整列 JSON 数组
    colors_json     JSON          DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'TypeHandler 真实库集成测试表';
