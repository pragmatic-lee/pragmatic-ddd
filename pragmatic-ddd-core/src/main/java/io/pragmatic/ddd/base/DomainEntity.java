package io.pragmatic.ddd.base;

import java.lang.annotation.*;

/**
 * Marks a class as a Domain Entity in the Pragmatic DDD framework.
 *
 * <p>This annotation provides semantic metadata that helps AI coding assistants
 * understand the business context of the entity. It is consumed by the
 * {@link io.pragmatic.ddd.visual.DomainModelVisualManager} for model visualization
 * and metadata export.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @DomainEntity(
 *     aggregateRoot = "Order",
 *     description = "订单商品项，表示订单中的单个商品",
 *     boundedContext = "order"
 * )
 * public class OrderItem extends EntityBase<Long> {
 *     // ...
 * }
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DomainEntity {

    /**
     * The name of the aggregate root this entity belongs to.
     * <p>For an aggregate root itself, this should be its own simple class name.</p>
     *
     * @return the aggregate root name
     */
    String aggregateRoot() default "";

    /**
     * A human-readable description of the entity's business purpose.
     * <p>Used by AI assistants to understand the domain context.</p>
     *
     * @return the entity description
     */
    String description() default "";

    /**
     * The bounded context this entity belongs to.
     * <p>Multiple bounded contexts may exist in a single application
     * (e.g., "order", "inventory", "shipping").</p>
     *
     * @return the bounded context name
     */
    String boundedContext() default "";
}
