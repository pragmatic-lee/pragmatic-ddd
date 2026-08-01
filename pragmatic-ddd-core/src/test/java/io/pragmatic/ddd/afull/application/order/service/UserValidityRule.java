package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.service.IUserValidityRule;
import io.pragmatic.ddd.base.RuleCheckResult;

/**
 * 用户有效性校验实现（Mock 外部用户服务）。
 *
 * @author wizard-lee
 */
public class UserValidityRule implements IUserValidityRule {

    @Override
    public RuleCheckResult check(String pin) {
        // Mock 外部用户服务调用，假设 pin 以 "invalid_" 开头表示无效用户
        if (pin == null || pin.startsWith("invalid_")) {
            return RuleCheckResult.fail(new Object[]{pin});
        }
        return RuleCheckResult.pass();
    }
}
