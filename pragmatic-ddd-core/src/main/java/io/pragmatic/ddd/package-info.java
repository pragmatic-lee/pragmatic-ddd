/**
 * Pragmatic DDD — a pragmatic, lightweight Domain-Driven Design framework for Java.
 *
 * <p>Core modules:</p>
 * <ul>
 *   <li>{@code base} — Entity, Value Object, Aggregate Root base classes and interfaces</li>
 *   <li>{@code rules} — Business rule engine with fluent API</li>
 *   <li>{@code event} — Domain event publishing and subscription infrastructure</li>
 *   <li>{@code subscriber} — Event subscriber and ordered execution management</li>
 *   <li>{@code repository} — Repository abstractions for data access</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Define an entity
 * @DomainEntity(aggregateRoot = "Order", description = "订单聚合根")
 * public class Order extends AggregateRoot<Long> {
 *     private String pin;
 *     private BigDecimal totalPrice;
 *
 *     public void payment() {
 *         // business logic
 *         collectEvent(new OrderPayedEvent(this));
 *     }
 *
 *     @Override
 *     public BrokenRuleRegistry brokenRuleRegistry() {
 *         return OrderBrokenRuleRegistry.INSTANCE;
 *     }
 *
 *     @Override
 *     public void validate() {
 *         new OrderEntityRule().check(this);
 *     }
 * }
 *
 * // Define business rules
 * public class OrderEntityRule extends EntityRule<Order> {
 *     public OrderEntityRule() {
 *         this.isBlank("pin", OrderBrokenRuleRegistry.PIN_IS_EMPTY);
 *         this.numberShouldGreaterThan("totalPrice", BigDecimal.ZERO,
 *             OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR);
 *     }
 * }
 * }</pre>
 *
 * @see io.pragmatic.ddd.base.AbstractEntity
 * @see io.pragmatic.ddd.rules.EntityRule
 * @see io.pragmatic.ddd.event.BaseDomainEvent
 * @since 2.0.0
 */
package io.pragmatic.ddd;
