package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.application.AbstractApplicationService;
import io.pragmatic.ddd.application.ICommandApplicationService;
import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.outbox.EagerOutboxPublisher;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.example.order.application.order.factory.OrderFactory;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderInput;
import io.pragmatic.ddd.example.order.application.order.rule.OrderRuleAssembler;
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

    public OrderWriteService(IEventManager eventManager,
                             IOutboxStore iOutboxStore,
                             IEventSerializer eventSerializer,
                             EagerOutboxPublisher eagerOutboxPublisher,
                             TransactionOperations txOps,
                             OrderFactory orderFactory,
                             OrderRuleAssembler orderRuleAssembler,
                             OrderRepository orderRepository) {
        super(eventManager, new OutboxCommandExecutor(iOutboxStore, txOps, eventSerializer, eagerOutboxPublisher));
        this.orderFactory = orderFactory;
        this.orderRuleAssembler = orderRuleAssembler;
        this.orderRepository = orderRepository;
    }

    /** 下单：创建并持久化订单。 */
    public Order placeOrder(CreateOrderInput input) {
        Order order = orderFactory.create(input);
        OrderRule rule = orderRuleAssembler.assemble();
        return super.execute(order, rule, orderRepository, t -> {
        });
    }
}
