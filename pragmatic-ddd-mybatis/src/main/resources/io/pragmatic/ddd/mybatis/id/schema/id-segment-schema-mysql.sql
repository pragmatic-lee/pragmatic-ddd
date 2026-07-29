CREATE TABLE IF NOT EXISTS id_segment (
    biz_key         VARCHAR(64)   NOT NULL,
    current_max_id  BIGINT        NOT NULL,
    step            INT           NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    remark          VARCHAR(128)  DEFAULT NULL,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ID 号段分配表';

-- 初始化示例：不同渠道错开起始位置；current_max_id 直接写为「起始值 - 1」，无独立 start_id 列。
-- 例：payment 起始 100 万 → current_max_id = 1000000 - 1 = 999999，首段即 [1000000, 1000999]。
INSERT INTO id_segment (biz_key, current_max_id, step, remark) VALUES
('order',    0,      1000, '订单渠道'),
('payment',  999999, 1000, '支付渠道，从 100 万起'),
('refund_a', 4999999, 500, '退款渠道 A，从 500 万起');
