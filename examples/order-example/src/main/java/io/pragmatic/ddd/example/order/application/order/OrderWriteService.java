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
import io.pragmatic.ddd.example.order.application.order.input.AddOrderItemInput;
import io.pragmatic.ddd.example.order.application.order.input.ChangeOrderAddressInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderInput;
import io.pragmatic.ddd.example.order.application.order.input.PayOrderInput;
import io.pragmatic.ddd.example.order.application.order.input.RemoveOrderItemInput;
import io.pragmatic.ddd.example.order.application.order.input.ShipOrderInput;
import io.pragmatic.ddd.example.order.application.order.input.UpdateOrderItemInput;
import io.pragmatic.ddd.example.order.application.order.updater.OrderAddressUpdater;
import io.pragmatic.ddd.example.order.application.order.updater.OrderAddItemUpdater;
import io.pragmatic.ddd.example.order.application.order.updater.OrderPayUpdater;
import io.pragmatic.ddd.example.order.application.order.updater.OrderRemoveItemUpdater;
import io.pragmatic.ddd.example.order.application.order.updater.OrderShipUpdater;
import io.pragmatic.ddd.example.order.application.order.updater.OrderUpdateItemUpdater;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.rule.OrderRule;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.event.spi.IEventManager;
import org.springframework.stereotype.Service;

/**
 * 订单写服务：下单、改址、发货、支付与订单项维护命令的应用服务编排。
 * Input 接入 → 工厂建聚合 / Updater 改聚合 → 规则校验 → 仓储持久化 → 领域事件发布，置于同一事务单元。
 */
@Service
public class OrderWriteService extends AbstractApplicationService implements ICommandApplicationService {

    private final OrderFactory orderFactory;

    private final OrderRule orderRule;

    private final OrderRepository orderRepository;

    private final OrderAddressUpdater orderAddressUpdater;

    private final OrderShipUpdater orderShipUpdater;

    private final OrderPayUpdater orderPayUpdater;

    private final OrderAddItemUpdater orderAddItemUpdater;

    private final OrderUpdateItemUpdater orderUpdateItemUpdater;

    private final OrderRemoveItemUpdater orderRemoveItemUpdater;

    public OrderWriteService(IEventManager eventManager,
                             IOutboxStore iOutboxStore,
                             IEventSerializer eventSerializer,
                             EagerOutboxPublisher eagerOutboxPublisher,
                             TransactionOperations txOps,
                             OrderFactory orderFactory,
                             OrderRule orderRule,
                             OrderRepository orderRepository,
                             OrderAddressUpdater orderAddressUpdater,
                             OrderShipUpdater orderShipUpdater,
                             OrderPayUpdater orderPayUpdater,
                             OrderAddItemUpdater orderAddItemUpdater,
                             OrderUpdateItemUpdater orderUpdateItemUpdater,
                             OrderRemoveItemUpdater orderRemoveItemUpdater) {
        super(eventManager, new OutboxCommandExecutor(iOutboxStore, txOps, eventSerializer, eagerOutboxPublisher));
        this.orderFactory = orderFactory;
        this.orderRule = orderRule;
        this.orderRepository = orderRepository;
        this.orderAddressUpdater = orderAddressUpdater;
        this.orderShipUpdater = orderShipUpdater;
        this.orderPayUpdater = orderPayUpdater;
        this.orderAddItemUpdater = orderAddItemUpdater;
        this.orderUpdateItemUpdater = orderUpdateItemUpdater;
        this.orderRemoveItemUpdater = orderRemoveItemUpdater;
    }

    /** 下单：创建并持久化订单。 */
    public Order placeOrder(CreateOrderInput input) {
        Order order = orderFactory.create(input);
        return super.execute(order, orderRule, orderRepository, t -> {
        });
    }

    /** 变更收货地址：加载聚合后经 Updater 完成 Input→Address 转换与充血方法调用，再统一校验与持久化。 */
    public Order changeOrderAddress(Long orderId, ChangeOrderAddressInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderAddressUpdater.apply(t, input));
    }

    /** 预校验变更收货地址：不落库、不发布，仅返回结构化校验结果。 */
    public DryRunResult tryChangeOrderAddress(Long orderId, ChangeOrderAddressInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.tryExecute(order, orderRule, orderRepository, t -> orderAddressUpdater.apply(t, input));
    }

    /** 发货：加载聚合后经 Updater 完成 Input→LogisticsInfo 转换与充血方法调用，再统一校验与持久化。 */
    public Order shipOrder(Long orderId, ShipOrderInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderShipUpdater.apply(t, input));
    }

    /** 预校验发货：不落库、不发布，仅返回结构化校验结果。 */
    public DryRunResult tryShipOrder(Long orderId, ShipOrderInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.tryExecute(order, orderRule, orderRepository, t -> orderShipUpdater.apply(t, input));
    }

    /** 支付：加载聚合后经 Updater 完成 Input→PaymentInfo 转换与充血方法调用，再统一校验与持久化。 */
    public Order payOrder(Long orderId, PayOrderInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input));
    }

    /** 预校验支付：不落库、不发布，仅返回结构化校验结果。 */
    public DryRunResult tryPayOrder(Long orderId, PayOrderInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.tryExecute(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input));
    }

    /** 新增订单项：加载聚合后经 Updater 完成 Input→OrderItem 转换、总额重算与充血方法调用，再统一校验与持久化。 */
    public Order addItem(Long orderId, AddOrderItemInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderAddItemUpdater.apply(t, input));
    }

    /** 预校验新增订单项：不落库、不发布，仅返回结构化校验结果。 */
    public DryRunResult tryAddItem(Long orderId, AddOrderItemInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.tryExecute(order, orderRule, orderRepository, t -> orderAddItemUpdater.apply(t, input));
    }

    /** 更新订单项：加载聚合后经 Updater 完成数量更新、总额重算与充血方法调用，再统一校验与持久化。 */
    public Order updateItem(Long orderId, UpdateOrderItemInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderUpdateItemUpdater.apply(t, input));
    }

    /** 预校验更新订单项：不落库、不发布，仅返回结构化校验结果。 */
    public DryRunResult tryUpdateItem(Long orderId, UpdateOrderItemInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.tryExecute(order, orderRule, orderRepository, t -> orderUpdateItemUpdater.apply(t, input));
    }

    /** 移除订单项：加载聚合后经 Updater 完成项移除、剩余项总额重算与充血方法调用，再统一校验与持久化。 */
    public Order removeItem(Long orderId, RemoveOrderItemInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderRemoveItemUpdater.apply(t, input));
    }

    /** 预校验移除订单项：不落库、不发布，仅返回结构化校验结果。 */
    public DryRunResult tryRemoveItem(Long orderId, RemoveOrderItemInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.tryExecute(order, orderRule, orderRepository, t -> orderRemoveItemUpdater.apply(t, input));
    }
}
