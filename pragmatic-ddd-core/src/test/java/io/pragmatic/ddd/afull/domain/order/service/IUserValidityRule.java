package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.base.IDomainService;
import io.pragmatic.ddd.base.RuleCheckResult;

/**
 * 用户有效性校验契约。
 * <p>校验失败时使用 {@link io.pragmatic.ddd.afull.domain.order.model.OrderBrokenRuleRegistry#USER_NOT_VALID} 错误码。
 *
 * @author wizard-lee
 */
public interface IUserValidityRule extends IDomainService {

    /**
     * 校验指定用户是否存在且有效。
     *
     * @param pin 用户 PIN
     * @return 校验结果
     */
    RuleCheckResult check(String pin);
}
