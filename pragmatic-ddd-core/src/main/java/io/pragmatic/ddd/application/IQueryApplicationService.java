package io.pragmatic.ddd.application;

/**
 * 查询应用服务标记接口。
 *
 * <p>实现此接口的应用服务类，其方法应是 Query（读操作），
 * 直接通过 {@link io.pragmatic.ddd.repository.query.IAggregateQuery} 系列接口查询，
 * 不涉及聚合根状态修改。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   public class OrderQueryService implements IQueryApplicationService {
 *       private final OrderQuery query;   // extends IAggregateQuery<Long, OrderProjection, ...>
 *       // ...
 *   }
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public interface IQueryApplicationService {
    // 标记接口，不添加额外 API
}
