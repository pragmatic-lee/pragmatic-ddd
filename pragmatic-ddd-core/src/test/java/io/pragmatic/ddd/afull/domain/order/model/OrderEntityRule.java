package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.base.RuleCheckResult;
import io.pragmatic.ddd.rules.ActiveStatus;
import io.pragmatic.ddd.rules.EntityRule;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

/**
 * @author lixiaojing
 * @date 2021/3/1 5:30 下午
 */
public class OrderEntityRule extends EntityRule<Order> {

    public OrderEntityRule() {
        this.init();
    }

    @Override
    protected Order supplyOldEntity() {
        return null;
    }

    @Override
    public void init() {
        this.addRule(model -> RuleCheckResult.of(!StringUtils.isBlank(model.getPin())),
                OrderBrokenRuleRegistry.PIN_IS_EMPTY);

        this.addRule(model -> RuleCheckResult.of(model.getTotalPrice().compareTo(BigDecimal.ZERO) > 0),
                OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR);

        this.addRule(model -> RuleCheckResult.of(!model.getOrderItemList().isEmpty() && model.getOrderItemList().size() < 100),
                OrderBrokenRuleRegistry.ORDER_ITEM_ERROR,
                model -> ActiveStatus.ACTIVE);
    }
}
