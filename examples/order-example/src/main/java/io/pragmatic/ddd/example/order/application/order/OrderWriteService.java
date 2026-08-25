package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.application.AbstractApplicationService;
import io.pragmatic.ddd.application.DryRunResult;
import io.pragmatic.ddd.application.ICommandApplicationService;
import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.outbox.EagerOutboxPublisher;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.example.order.application.order.factory.OrderFactory;
import io.pragmatic.ddd.example.order.application.order.input.ChangeOrderAddressInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderInput;
import io.pragmatic.ddd.example.order.application.order.rule.OrderRuleAssembler;
import io.pragmatic.ddd.example.order.application.order.updater.OrderAddressUpdater;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.rule.OrderRule;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.event.spi.IEventManager;
import org.springframework.stereotype.Service;

/**
 * 订单写服务：下单命令的应用服务编排。
 * Input 接入 → 工厂建聚合 → 规则校验 → 仓储持久化 → 领域事件发布，置于同一事务单元。
 */
@Service
public class OrderWriteService extends AbstractApplicationService implements ICommandApplicationService {

    private final OrderFactory orderFactory;

    private final OrderRuleAssembler orderRuleAssembler;

    private final OrderRepository orderRepository;

    private final OrderAddressUpdater orderAddressUpdater;

    public OrderWriteService(IEventManager eventManager,
                             IOutboxStore iOutboxStore,
                             IEventSerializer eventSerializer,
                             EagerOutboxPublisher eagerOutboxPublisher,
                             TransactionOperations txOps,
                             OrderFactory orderFactory,
                             OrderRuleAssembler orderRuleAssembler,
                             OrderRepository orderRepository,
                             OrderAddressUpdater orderAddressUpdater) {
        super(eventManager, new OutboxCommandExecutor(iOutboxStore, txOps, eventSerializer, eagerOutboxPublisher));
        this.orderFactory = orderFactory;
        this.orderRuleAssembler = orderRuleAssembler;
        this.orderRepository = orderRepository;
        this.orderAddressUpdater = orderAddressUpdater;
    }

    /** 下单：创建并持久化订单。 */
    public Order placeOrder(CreateOrderInput input) {
        Order order = orderFactory.create(input);
        OrderRule rule = orderRuleAssembler.assemble();
        return super.execute(order, rule, orderRepository, t -> {
        });
    }

    /** 变更收货地址：加载聚合后经 Updater 完成 Input→Address 转换与充血方法调用，再统一校验与持久化。 */
    public Order changeOrderAddress(Long orderId, ChangeOrderAddressInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        OrderRule rule = orderRuleAssembler.assemble();
        return super.execute(order, rule, orderRepository, t -> orderAddressUpdater.apply(t, input));
    }

    /** 预校验变更收货地址：不落库、不发布，仅返回结构化校验结果。 */
    public DryRunResult tryChangeOrderAddress(Long orderId, ChangeOrderAddressInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        OrderRule rule = orderRuleAssembler.assemble();
        return super.tryExecute(order, rule, orderRepository, t -> orderAddressUpdater.apply(t, input));
    }
}
