package io.pragmatic.ddd.mybatis.id;

import org.apache.ibatis.annotations.Param;

/**
 * ID 号段 Mapper 契约接口（可选）：仅声明方法签名，具体 SQL 由同包同名 XML 提供。
 * 框架默认走传统纯 XML 直调方式（DbSegmentAllocator 按 namespace.statementId 直接调用），
 * 使用方无需引入本接口；若偏好接口式仍可 addMapper + getMapper，与纯 XML 扫描不冲突。
 *
 * @author wizard-lee
 */
public interface IdSegmentMapper {

    /** 行锁读取当前号段（需处于事务内 + InnoDB 引擎才生效；事务由 DbSegmentAllocator 自管）。 */
    IdSegmentEntity selectForUpdate(@Param("bizKey") String bizKey);

    /** 推进号段上限（行锁已保证并发安全，version 仅作审计 +1）。 */
    int incrementMax(@Param("bizKey") String bizKey, @Param("newMax") long newMax);
}
