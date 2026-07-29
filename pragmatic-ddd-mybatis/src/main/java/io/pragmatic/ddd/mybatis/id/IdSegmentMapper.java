package io.pragmatic.ddd.mybatis.id;

import org.apache.ibatis.annotations.Param;

/**
 * ID 号段 Mapper 契约接口（与具体数据库无关的通用定义）。
 * 本接口仅声明方法签名，不含任何 SQL；具体 SQL 由与本接口同包同名
 * （IdSegmentMapper.xml）的 XML 提供，按目标数据库实现（当前为 MySQL）。
 * 未来支持其他数据库时，只需替换该 XML（保持 namespace 为本接口全限定名），
 * 使用方（如 DbSegmentAllocator）与注册方式均无需改动。
 * 本接口不标注 @Mapper，由使用方手动 addMapper 注册（与 outbox 一致）。
 */
public interface IdSegmentMapper {

    /** 行锁读取当前号段（需处于事务内 + InnoDB 引擎才生效；事务由 DbSegmentAllocator 自管）。 */
    IdSegmentEntity selectForUpdate(@Param("bizKey") String bizKey);

    /** 推进号段上限（行锁已保证并发安全，version 仅作审计 +1）。 */
    int incrementMax(@Param("bizKey") String bizKey, @Param("newMax") long newMax);
}
