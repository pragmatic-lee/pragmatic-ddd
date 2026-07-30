CREATE TABLE IF NOT EXISTS id_segment (
    biz_key         VARCHAR(64)   NOT NULL,
    current_max_id  BIGINT        NOT NULL,
    step            INT           NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    remark          VARCHAR(128)  DEFAULT NULL,
    created_at      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ID 号段分配表';
