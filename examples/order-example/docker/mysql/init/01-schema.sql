-- order-example 建库建表脚本（幂等，可重复执行）
-- 业务表 DDL 与 docs/design/examples/order-example-db-design.md 第 7 节一致；
-- 框架支撑表与 pragmatic-ddd-mybatis 模块 schema 脚本一致。

CREATE DATABASE IF NOT EXISTS order_example
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE order_example;

CREATE TABLE IF NOT EXISTS t_order (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '聚合根标识',
    customer          JSON         NULL COMMENT 'Customer 值对象',
    status            INT          NOT NULL COMMENT '订单状态 CODE（OrderStatus.value）',
    shipping_address  JSON         NULL COMMENT 'Address 值对象',
    currency          VARCHAR(8)   NULL COMMENT '币种（冗余，便于查询）',
    total_amount      JSON         NULL COMMENT 'Money 值对象',
    remark            VARCHAR(512) NULL COMMENT '订单备注',
    paid_at           DATETIME     NULL COMMENT '支付时间',
    cancel_reason     VARCHAR(256) NULL COMMENT '取消原因',
    payment_method    INT          NULL COMMENT '支付方式 CODE（PaymentMethod.value）',
    logistics_info    JSON         NULL COMMENT 'LogisticsInfo 值对象',
    payment_serial_no VARCHAR(64)  NULL COMMENT '支付流水号',
    platform_discount JSON         NULL COMMENT 'Money 值对象',
    actual_amount     JSON         NULL COMMENT 'Money 值对象',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by        VARCHAR(64)  NULL COMMENT '创建人（AbstractEntity.createdBy）',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by        VARCHAR(64)  NULL COMMENT '更新人（AbstractEntity.updatedBy）',
    version           BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_paid_at (paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单聚合根';

CREATE TABLE IF NOT EXISTS t_order_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单项实体标识',
    order_id     BIGINT       NOT NULL COMMENT '关联订单 id',
    product_id   BIGINT       NULL COMMENT '商品标识',
    product_name VARCHAR(128) NULL COMMENT '商品名',
    spec         VARCHAR(128) NULL COMMENT '规格',
    price        JSON         NULL COMMENT 'Money 值对象（单价）',
    quantity     INT          NOT NULL DEFAULT 0 COMMENT '数量',
    subtotal     JSON         NULL COMMENT 'Money 值对象（小计）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by   VARCHAR(64)  NULL COMMENT '创建人（AbstractEntity.createdBy）',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by   VARCHAR(64)  NULL COMMENT '更新人（AbstractEntity.updatedBy）',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项（一对多，order_id 逻辑关联 t_order，无外键约束）';

CREATE TABLE IF NOT EXISTS id_segment (
    biz_key         VARCHAR(64)   NOT NULL,
    current_max_id  BIGINT        NOT NULL,
    step            INT           NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    remark          VARCHAR(128)  DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ID 号段分配表';

CREATE TABLE IF NOT EXISTS outbox_message (
    id             VARCHAR(36)   NOT NULL,
    aggregate_id   VARCHAR(128)  NOT NULL,
    aggregate_type VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(255)  NOT NULL,
    entity_id      VARCHAR(128),
    payload        LONGTEXT      NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    queue          INT           NOT NULL DEFAULT 0,
    created_at     DATETIME(3)   NOT NULL,
    claimed_at     DATETIME(3),
    sent_at        DATETIME(3),
    last_error     VARCHAR(2000),
    claim_token    VARCHAR(36),
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at),
    INDEX idx_claim_token (claim_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
