package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.afull.domain.order.service.ICreditLimitRule;
import io.pragmatic.ddd.afull.domain.order.service.IUserValidityRule;
import io.pragmatic.ddd.base.MessageCode;
import io.pragmatic.ddd.base.RuleCheckResult;
import io.pragmatic.ddd.rules.ActiveStatus;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.IActiveRuleCondition;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

/**
 * 订单实体规则容器，组合内部不变量与外部依赖校验规则。
 *
 * @author wizard-lee
 */
public class OrderEntityRule extends EntityRule<Order> {

    private static final BigDecimal CREDIT_CHECK_THRESHOLD = new BigDecimal("100000");

    private final IUserValidityRule userValidityRule;
    private final ICreditLimitRule creditLimitRule;

    public OrderEntityRule() {
        this(null, null);
    }

    public OrderEntityRule(IUserValidityRule userValidityRule) {
        this(userValidityRule, null);
    }

    public OrderEntityRule(IUserValidityRule userValidityRule, ICreditLimitRule creditLimitRule) {
        this.userValidityRule = userValidityRule;
        this.creditLimitRule = creditLimitRule;
        this.init();
    }

    @Override
    public void init() {
        // === 内部不变量校验 ===
        this.addRule((model, old) -> RuleCheckResult.of(!StringUtils.isBlank(model.getPin())),
                OrderBrokenRuleRegistry.PIN_IS_EMPTY);

        this.addRule((model, old) -> RuleCheckResult.of(model.getTotalPrice().compareTo(BigDecimal.ZERO) > 0),
                OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR);

        this.addRule((model, old) -> RuleCheckResult.of(
                        !model.getOrderItemList().isEmpty() && model.getOrderItemList().size() < 100),
                OrderBrokenRuleRegistry.ORDER_ITEM_ERROR);

        // === 外部依赖校验（校验规则领域服务） ===
        if (userValidityRule != null) {
            this.addRule(
                    (order, old) -> userValidityRule.check(order.getPin()),
                    OrderBrokenRuleRegistry.USER_NOT_VALID
            );
        }

        if (creditLimitRule != null) {
            // 信用额度校验：仅在创建订单（isNew）且金额 > 10 万时激活
            this.addRule(
                    (order, old) -> creditLimitRule.check(order.getPin(), order.getTotalPrice()),
                    OrderBrokenRuleRegistry.CREDIT_LIMIT_EXCEEDED,
                    (order, old) -> order.isNew()
                            && order.getTotalPrice().compareTo(CREDIT_CHECK_THRESHOLD) > 0
                            ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE
            );
        }
    }
}
