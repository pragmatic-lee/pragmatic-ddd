package io.pragmatic.ddd.rules;

import java.lang.annotation.*;

/**
 * Marks a method as a Business Rule within a domain entity or rule class.
 *
 * <p>This annotation provides metadata about business rules that can be consumed
 * by AI coding assistants and the model visualization system. It helps document
 * the intent, error codes, and messages of each business rule.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @BusinessRule(
 *     description = "订单总金额必须大于0",
 *     errorCode = "TOTAL_PRICE_ERROR",
 *     errorMessage = "订单总金额不能为0"
 * )
 * public boolean totalPriceValid(Order order) {
 *     return order.getTotalPrice().compareTo(BigDecimal.ZERO) > 0;
 * }
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessRule {

    /**
     * A human-readable description of what this rule validates.
     *
     * @return the rule description
     */
    String description() default "";

    /**
     * The error code used when this rule is broken.
     * <p>Corresponds to a key in the entity's {@code BrokenRuleMessage} class.</p>
     *
     * @return the error code
     */
    String errorCode() default "";

    /**
     * A human-readable error message shown when this rule is broken.
     *
     * @return the error message
     */
    String errorMessage() default "";
}
