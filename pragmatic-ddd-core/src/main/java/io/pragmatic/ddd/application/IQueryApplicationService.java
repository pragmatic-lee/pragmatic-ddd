package io.pragmatic.ddd.application;

/**
 * 查询应用服务标记接口，实现类的查询方法直接通过 IAggregateQuery 查询，不涉及聚合根状态修改。
 *
 * @author wizard-lee
 */
public interface IQueryApplicationService {
    // 标记接口，不添加额外 API
}
