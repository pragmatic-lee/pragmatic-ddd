package io.pragmatic.ddd.mybatis.id;

/**
 * ID 号段 Mapper 的 MySQL 实现（本模块唯一内置实现）。
 * 继承通用 {@link IdSegmentMapper} 契约，SQL 固化于 MysqlIdSegmentMapper.xml。
 * 本接口不标注 @Mapper，由使用方在构建 SqlSessionFactory 后调用
 * configuration.addMapper(MysqlIdSegmentMapper.class) 注册。
 */
public interface MysqlIdSegmentMapper extends IdSegmentMapper {
}
