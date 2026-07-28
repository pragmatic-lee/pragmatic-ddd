package io.pragmatic.ddd.event;

import java.lang.annotation.*;

/**
 * Marks a method as a trigger point for a domain event.
 *
 * <p>When a business method produces a domain event, this annotation documents
 * the relationship between the method and the event. It helps AI assistants
 * understand the event flow and can be used for automated documentation
 * and architecture analysis.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @EventTrigger(
 *     eventClass = OrderPayedEvent.class,
 *     description = "支付完成后触发订单已支付事件",
 *     afterMethod = "payment"
 * )
 * public void payment() {
 *     this.status = OrderStatus.PAYED;
 *     collectEvent(new OrderPayedEvent(this));
 * }
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventTrigger {

    /**
     * The domain event class that is triggered.
     *
     * @return the event class
     */
    Class<?> eventClass() default Void.class;

    /**
     * A human-readable description of when and why this event is triggered.
     *
     * @return the event description
     */
    String description() default "";

    /**
     * The name of the method that triggers this event.
     * <p>Used for documentation and tracing purposes.</p>
     *
     * @return the triggering method name
     */
    String afterMethod() default "";
}
