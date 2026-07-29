package io.pragmatic.ddd.mybatis.id;

import org.apache.ibatis.annotations.Param;

/**
 * ID 号段 Mapper 通用接口（与具体数据库无关的契约定义）。
 * 本接口仅声明方法签名，不含任何 SQL；具体 SQL 由 MysqlIdSegmentMapper 的 XML 提供。
 * 本接口及具体实现接口均不标注 @Mapper，由使用方手动 addMapper 注册（与 outbox 一致）。
 */
public interface IdSegmentMapper {

    /** 行锁读取当前号段（需处于事务内 + InnoDB 引擎才生效；事务由 DbSegmentAllocator 自管）。 */
    IdSegmentEntity selectForUpdate(@Param("bizKey") String bizKey);

    /** 推进号段上限（行锁已保证并发安全，version 仅作审计 +1）。 */
    int incrementMax(@Param("bizKey") String bizKey, @Param("newMax") long newMax);
}
