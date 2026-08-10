package io.pragmatic.ddd.service;

/**
 * 校验规则领域服务基类接口（第二类）。
 * <p>契约继承本接口即声明其为领域校验规则服务，由应用服务作为 {@code IRule} 参数传入执行。
 *
 * @author wizard-lee
 */
public interface IRuleValidatorService extends IDomainService {
}
