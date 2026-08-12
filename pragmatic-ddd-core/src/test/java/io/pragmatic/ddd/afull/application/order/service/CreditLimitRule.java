package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.service.ICreditLimitRule;
import io.pragmatic.ddd.rules.RuleCheckResult;

import java.math.BigDecimal;

/**
 * 信用额度校验实现（Mock 外部信用系统）。
 * <p>Mock 规则：pin 以 "vip_" 开头视为高信用用户（额度 50 万），
 * 其他用户可用额度固定为 10 万。
 *
 * @author wizard-lee
 */
public class CreditLimitRule implements ICreditLimitRule {

    private static final BigDecimal DEFAULT_CREDIT_LIMIT = new BigDecimal("100000");
    private static final BigDecimal VIP_CREDIT_LIMIT = new BigDecimal("500000");

    @Override
    public RuleCheckResult check(String pin, BigDecimal orderAmount) {
        // Mock 外部信用系统调用
        BigDecimal availableCredit = pin != null && pin.startsWith("vip_")
                ? VIP_CREDIT_LIMIT
                : DEFAULT_CREDIT_LIMIT;

        if (availableCredit.compareTo(orderAmount) >= 0) {
            return RuleCheckResult.pass();
        }
        return RuleCheckResult.fail(new Object[]{pin, orderAmount});
    }
}
