package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 应用服务便捷基类。
 *
 * <p>提供 ICommandExecutor 和 IUnitOfWork 的快捷访问。
 * 不强制继承，也可以直接组合使用 ICommandExecutor / IUnitOfWork。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   public class OrderApplicationService extends AbstractApplicationService {
 *
 *       // 默认：CommandExecutor（保存后直接发布）+ UnitOfWork（保存后统一发布）
 *       public OrderApplicationService(IEventManager eventManager) {
 *           super(eventManager);
 *       }
 *
 *       // 可选：注入 OutboxCommandExecutor（同事务落 outbox + 提交后推送）
 *       public OrderApplicationService(IEventManager eventManager,
 *                                       OutboxCommandExecutor outboxExecutor) {
 *           super(eventManager, outboxExecutor);
 *       }
 *
 *       // 全可选：注入 ICommandExecutor + IUnitOfWork 工厂（含 OutboxUnitOfWork）
 *       public OrderApplicationService(IEventManager eventManager,
 *                                       ICommandExecutor commandExecutor,
 *                                       Supplier<IUnitOfWork> uowFactory) {
 *           super(eventManager, commandExecutor, uowFactory);
 *       }
 *
 *       public void createOrder(CreateOrderCommand cmd) {
 *           Order order = Order.create(cmd);
 *           execute(order, orderRule, orderRepo, o -> {});
 *       }
 *   }
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public abstract class AbstractApplicationService {

    protected final IEventManager eventManager;
    protected final ICommandExecutor commandExecutor;
    protected final Supplier<IUnitOfWork> unitOfWorkFactory;

    /**
     * 向后兼容构造器：默认使用 {@link CommandExecutor}（保存后直接发布）
     * 与默认 {@link UnitOfWork}。
     *
     * @param eventManager 事件管理器
     */
    protected AbstractApplicationService(IEventManager eventManager) {
        this(eventManager, new CommandExecutor(eventManager), () -> new UnitOfWork(eventManager));
    }

    /**
     * 可选构造器：注入任意 {@link ICommandExecutor} 实现
     * （含 {@code OutboxCommandExecutor} / 自定义 / 追踪包装）；UnitOfWork 仍用默认。
     *
     * @param eventManager   事件管理器（供 {@link #beginUnitOfWork()} 使用）
     * @param commandExecutor 命令执行器实现
     */
    protected AbstractApplicationService(IEventManager eventManager,
                                         ICommandExecutor commandExecutor) {
        this(eventManager, commandExecutor, () -> new UnitOfWork(eventManager));
    }

    /**
     * 全可选构造器：注入 {@link ICommandExecutor} 与 {@link IUnitOfWork} 工厂
     * （含 {@code OutboxUnitOfWork} / 自定义）。
     *
     * @param eventManager      事件管理器（供 {@link #beginUnitOfWork()} 使用）
     * @param commandExecutor   命令执行器实现
     * @param unitOfWorkFactory 工作单元工厂（每次 {@link #beginUnitOfWork()} 创建新实例）
     */
    protected AbstractApplicationService(IEventManager eventManager,
                                         ICommandExecutor commandExecutor,
                                         Supplier<IUnitOfWork> unitOfWorkFactory) {
        this.eventManager = eventManager;
        this.commandExecutor = commandExecutor;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    /**
     * 执行单聚合根命令。
     *
     * @param aggregateRoot 聚合根实例
     * @param rule          业务规则（可为 null）
     * @param repository    对应仓储
     * @param domainLogic   领域逻辑
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     * @return 执行后的聚合根
     */
    protected <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        return commandExecutor.execute(aggregateRoot, rule, repository, domainLogic);
    }

    /**
     * 创建新的工作单元（用于跨聚合根事务编排）。
     *
     * @return 新的工作单元实例（默认 {@link UnitOfWork}，可注入 {@code OutboxUnitOfWork} 等）
     */
    protected IUnitOfWork beginUnitOfWork() {
        return unitOfWorkFactory.get();
    }
}
