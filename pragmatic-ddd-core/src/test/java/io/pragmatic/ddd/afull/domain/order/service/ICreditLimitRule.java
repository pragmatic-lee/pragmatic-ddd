package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.IDomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;

import java.math.BigDecimal;

/**
 * 信用额度校验契约。
 * <p>校验失败时使用 {@link io.pragmatic.ddd.afull.domain.order.model.OrderBrokenRuleRegistry#CREDIT_LIMIT_EXCEEDED} 错误码。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.RULE_VALIDATOR,
        targetName = "Order",
        description = "校验用户信用额度是否足够覆盖订单金额")
public interface ICreditLimitRule extends IDomainService {

    /**
     * 校验用户信用额度是否足够覆盖订单金额。
     *
     * @param pin         用户 PIN
     * @param orderAmount 订单金额
     * @return 校验结果
     */
    RuleCheckResult check(String pin, BigDecimal orderAmount);
}
