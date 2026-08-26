-- order-example 初始化数据脚本（幂等，可重复执行）
-- id_segment 必须预置 order 渠道初始行，否则 DbSegmentAllocator.allocateNext 的
-- selectForUpdate 返回空会 NPE；INSERT IGNORE 保证迁移环境已有行时不被覆盖。

USE order_example;

INSERT IGNORE INTO id_segment (biz_key, current_max_id, step, remark)
VALUES ('order', 0, 1000, '订单号');
