package io.pragmatic.ddd.event;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/**
 * 领域事件不可变基类。
 *
 * <p>所有字段通过构造函数注入，不提供任何 setter。
 * 使用 Lombok {@code @Getter} 自动生成所有 getter。</p>
 *
 * <p><b>子类模式（字段少时用构造器）：</b></p>
 * <pre>{@code
 * @EventName("OrderPayedEvent")
 * public class OrderPayedEvent extends BaseDomainEvent {
 *     private final long orderId;
 *
 *     public OrderPayedEvent(long orderId) {
 *         super(String.valueOf(orderId));
 *         this.orderId = orderId;
 *     }
 *
 *     protected OrderPayedEvent() { }
 *
 *     public long getOrderId() { return orderId; }
 * }
 * }</pre>
 *
 * <p><b>子类模式（字段多时用静态工厂）：</b></p>
 * <pre>{@code
 * @EventName("OrderCreatedEvent")
 * public class OrderCreatedEvent extends BaseDomainEvent {
 *     private final long orderId;
 *     private final BigDecimal totalPrice;
 *
 *     public static OrderCreatedEvent from(Order order) {
 *         return new OrderCreatedEvent(String.valueOf(order.getId()), order.getId(), order.getTotalPrice());
 *     }
 *
 *     public OrderCreatedEvent(String businessId, long orderId, BigDecimal totalPrice) {
 *         super(businessId);
 *         this.orderId = orderId;
 *         this.totalPrice = totalPrice;
 *     }
 *
 *     protected OrderCreatedEvent() { }
 * }
 * }</pre>
 */
@Getter
public abstract class BaseDomainEvent implements IDomainEvent {

    private final String eventId;
    private final String businessId;
    private final Instant occurredOn;

    /** 由 EntityBase.publishEvent() / Fastjson2 反序列化设置，子类不应主动赋值 */
    public String actionName;
    public long version;

    /** 常规构造：自动生成 eventId + 记录当前时间 */
    protected BaseDomainEvent(String businessId) {
        this(businessId, UUID.randomUUID().toString(), Instant.now());
    }

    /** 事件重放构造：指定 businessId + eventId + 时间 */
    protected BaseDomainEvent(String businessId, String eventId, Instant occurredOn) {
        this.eventId = eventId;
        this.businessId = businessId;
        this.occurredOn = occurredOn;
    }

    /** Fastjson2 Feature.FieldBased 反序列化入口 */
    protected BaseDomainEvent() {
        this.eventId = null;
        this.businessId = null;
        this.occurredOn = null;
    }
}
