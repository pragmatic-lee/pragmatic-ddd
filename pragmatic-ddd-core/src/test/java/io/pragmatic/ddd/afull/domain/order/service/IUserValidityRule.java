package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.ICheckRuleService;
import io.pragmatic.ddd.service.DomainServiceCategory;

/**
 * 用户有效性校验契约。
 * <p>校验失败时使用 {@link io.pragmatic.ddd.afull.domain.order.model.OrderBrokenRuleRegistry#USER_NOT_VALID} 错误码。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.BUSINESS_RULE,
        targetName = "Order",
        description = "校验指定用户是否存在且有效")
public interface IUserValidityRule extends ICheckRuleService<String> {

    /**
     * 校验指定用户是否存在且有效。
     *
     * @param pin 用户 PIN
     * @return 校验结果
     */
    RuleCheckResult check(String pin);

    /**
     * 实现 {@code ICheckRule} 抽象契约，忽略新旧模型对比，委托给 {@link #check(String)}。
     *
     * @param newPin 当前用户 PIN
     * @param oldPin 修改前的用户 PIN，本规则不关心，传 null
     * @return 校验结果
     */
    @Override
    default RuleCheckResult check(String newPin, String oldPin) {
        return check(newPin);
    }
}
