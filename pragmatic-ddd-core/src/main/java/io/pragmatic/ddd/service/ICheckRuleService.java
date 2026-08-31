package io.pragmatic.ddd.service;

import io.pragmatic.ddd.rules.ICheckRule;

/**
 * 业务规则校验领域服务基类接口（BUSINESS_RULE 类）。
 * <p>契约继承本接口即声明其为领域业务规则校验服务，由应用服务作为 {@code ICheckRule} 参数传入执行。
 *
 * @author wizard-lee
 */
public interface ICheckRuleService<T> extends IDomainService, ICheckRule<T> {
}
