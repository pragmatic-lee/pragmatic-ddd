package io.pragmatic.ddd.application;

/**
 * 命令应用服务标记接口。
 *
 * <p>实现此接口的应用服务类，其方法应是 Command（写操作），
 * 操作聚合根并通过 {@link ICommandExecutor} 或 {@link UnitOfWork} 执行。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   public class OrderCommandService implements ICommandApplicationService {
 *       private final ICommandExecutor executor;
 *       // ...
 *   }
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public interface ICommandApplicationService {
    // 标记接口，不添加额外 API
}
