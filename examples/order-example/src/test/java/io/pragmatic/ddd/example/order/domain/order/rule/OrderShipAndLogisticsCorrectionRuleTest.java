package io.pragmatic.ddd.example.order.domain.order.rule;

import io.pragmatic.ddd.base.BrokenRule;
import io.pragmatic.ddd.example.order.domain.order.event.OrderLogisticsCorrectedEvent;
import io.pragmatic.ddd.example.order.domain.order.event.OrderShippedEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.model.enums.ShipmentStatus;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.LogisticsInfo;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.PaymentInfo;
import io.pragmatic.ddd.example.order.domain.order.param.OrderInitData;
import io.pragmatic.ddd.example.order.domain.order.repository.IOrderRepository;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import io.pragmatic.ddd.rules.RuleCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单发货与物流信息修正规则校验：验证重复发货被拦截、已发货区间允许修正且状态不被推进。
 *
 * @author wizard-lee
 */
class OrderShipAndLogisticsCorrectionRuleTest {

    private static final Long ORDER_ID = 1000000000001L;

    private static final LocalDateTime SHIPPED_AT = LocalDateTime.of(2026, 9, 14, 10, 0);

    private StubOrderRepository orderRepository;

    private OrderRule orderRule;

    @BeforeEach
    void setUp() {
        this.orderRepository = new StubOrderRepository();
        this.orderRule = new OrderRule(new AlwaysPassPermissionService(), this.orderRepository);
    }

    /**
     * 已发货订单再次发货必须被拦截（本次修复的核心场景）。
     */
    @Test
    @DisplayName("已发货订单再次发货：拦截并给出 ORDER_SHIP_STATUS_INVALID")
    void shipAgainWhenAlreadyShippedShouldBeRejected() {
        Order order = this.newPaidOrder();
        this.orderRepository.stub(this.newShippedOrder());

        order.ship(this.logisticsInfo("SF0000001", "SF"));

        assertFalse(order.satisfiesRule(this.orderRule));
        assertTrue(this.brokenCodes(order).contains("ORDER_SHIP_STATUS_INVALID"));
    }

    /**
     * 待发货订单首次发货应放行。
     */
    @Test
    @DisplayName("待发货订单首次发货：放行")
    void shipWhenPendingShouldPass() {
        Order order = this.newPaidOrder();
        this.orderRepository.stub(this.newPendingOrder());

        order.ship(this.logisticsInfo("SF0000001", "SF"));

        assertTrue(order.satisfiesRule(this.orderRule));
    }

    /**
     * 已签收订单发货应被拦截（生命周期已推进到已完成）。
     */
    @Test
    @DisplayName("已签收订单发货：拦截")
    void shipWhenSignedShouldBeRejected() {
        Order order = this.newPaidOrder();
        Order persisted = this.newShippedOrder();
        persisted.sign();
        this.orderRepository.stub(persisted);

        order.ship(this.logisticsInfo("SF0000001", "SF"));

        assertFalse(order.satisfiesRule(this.orderRule));
        assertTrue(this.brokenCodes(order).contains("ORDER_SHIP_STATUS_INVALID"));
    }

    /**
     * 已发货订单修正物流信息应放行，且不推进物流状态、不发布发货事件。
     */
    @Test
    @DisplayName("已发货订单修正物流信息：放行且状态保持已发货")
    void correctLogisticsWhenShippedShouldPass() {
        Order persisted = this.newShippedOrder();
        Order order = this.newShippedOrder();
        this.orderRepository.stub(persisted);

        order.correctLogisticsInfo(this.logisticsInfo("YT0000002", "YTO"), "运单号录错");

        assertTrue(order.satisfiesRule(this.orderRule));
        assertEquals(ShipmentStatus.SHIPPED, order.getShipmentStatus());
        assertEquals("YT0000002", order.getLogisticsInfo().getTrackingNo());
        assertTrue(order.getDomainEvents().stream()
                .anyMatch(OrderLogisticsCorrectedEvent.class::isInstance));
        assertFalse(order.getDomainEvents().stream()
                .anyMatch(OrderShippedEvent.class::isInstance));
    }

    /**
     * 未发货订单修正物流信息应被拦截。
     */
    @Test
    @DisplayName("未发货订单修正物流信息：拦截")
    void correctLogisticsWhenPendingShouldBeRejected() {
        Order order = this.newPaidOrder();
        this.orderRepository.stub(this.newPendingOrder());

        order.correctLogisticsInfo(this.logisticsInfo("YT0000002", "YTO"), "运单号录错");

        assertFalse(order.satisfiesRule(this.orderRule));
        assertTrue(this.brokenCodes(order)
                .contains("ORDER_LOGISTICS_CORRECTION_STATUS_INVALID"));
    }

    /**
     * 已签收订单修正物流信息应被拦截。
     */
    @Test
    @DisplayName("已签收订单修正物流信息：拦截")
    void correctLogisticsWhenSignedShouldBeRejected() {
        Order order = this.newShippedOrder();
        Order persisted = this.newShippedOrder();
        persisted.sign();
        this.orderRepository.stub(persisted);

        order.correctLogisticsInfo(this.logisticsInfo("YT0000002", "YTO"), "运单号录错");

        assertFalse(order.satisfiesRule(this.orderRule));
        assertTrue(this.brokenCodes(order)
                .contains("ORDER_LOGISTICS_CORRECTION_STATUS_INVALID"));
    }

    /**
     * 修正内容与持久化态完全一致时应被拦截，避免刷出无意义事件。
     */
    @Test
    @DisplayName("修正内容无变化：拦截")
    void correctLogisticsWithSameContentShouldBeRejected() {
        Order persisted = this.newShippedOrder();
        Order order = this.newShippedOrder();
        this.orderRepository.stub(persisted);

        order.correctLogisticsInfo(this.logisticsInfo("SF0000001", "SF"), "重复提交");

        assertFalse(order.satisfiesRule(this.orderRule));
        assertTrue(this.brokenCodes(order)
                .contains("ORDER_LOGISTICS_CORRECTION_NO_CHANGE"));
    }

    private List<String> brokenCodes(Order order) {
        return order.getBrokenRules().stream()
                .map(BrokenRule::getName)
                .toList();
    }

    private LogisticsInfo logisticsInfo(String trackingNo, String companyCode) {
        return new LogisticsInfo(trackingNo, companyCode, "物流公司", SHIPPED_AT);
    }

    /**
     * 新建一张已支付的待发货订单，并清空下单阶段的操作标记。
     */
    private Order newPaidOrder() {
        Order order = this.newPendingOrder();
        order.pay(new PaymentInfo(
                "PAY-SERIAL-001",
                new Money(BigDecimal.ZERO, "CNY"),
                new Money(new BigDecimal("68.00"), "CNY"),
                PaymentMethod.WECHAT));
        order.clearWorkUnitState();
        return order;
    }

    /**
     * 新建一张已发货且已支付的订单。
     */
    private Order newShippedOrder() {
        Order order = this.newPaidOrder();
        order.ship(this.logisticsInfo("SF0000001", "SF"));
        order.clearWorkUnitState();
        return order;
    }

    private Order newPendingOrder() {
        OrderInitData data = new OrderInitData();
        data.setCustomer(new Customer(10001L, "测试客户"));
        data.setShippingAddress(new Address(
                "浙江省", "杭州市", "西湖区", "文三路 100 号", "张三", "13800000000"));
        data.setRemark(null);
        data.setPaymentMethod(PaymentMethod.WECHAT);
        data.setTotalAmount(new Money(new BigDecimal("68.00"), "CNY"));
        data.setOrderItems(List.of(
                new OrderItem(1001L, "云南小粒咖啡豆", null,
                        new Money(new BigDecimal("68.00"), "CNY"), 1)));
        Order order = new Order(data, ORDER_ID);
        order.clearWorkUnitState();
        return order;
    }

    /**
     * 只提供旧快照的仓储桩：findById 返回预设快照，写操作不落库。
     */
    private static class StubOrderRepository implements IOrderRepository {

        private Order stub;

        private void stub(Order order) {
            this.stub = order;
        }

        @Override
        public Order findById(Long id) {
            return this.stub;
        }

        @Override
        public void insert(Order aggregateRoot) {
            // 桩实现：不落库
        }

        @Override
        public void update(Order aggregateRoot) {
            // 桩实现：不落库
        }

        @Override
        public void remove(Order aggregateRoot) {
            // 桩实现：不落库
        }
    }

    /**
     * 下单资格恒定通过的领域服务桩，隔离外部依赖。
     */
    private static class AlwaysPassPermissionService implements IOrderCustomerPermissionService {

        @Override
        public RuleCheckResult verifyOrderCreatePermission(Customer customer) {
            return RuleCheckResult.pass();
        }
    }
}
