package io.pragmatic.ddd.service;

/**
 * 领域工厂 / 能力供给领域服务基类接口（第四类）。
 * <p>契约继承本接口即声明其为领域能力供给服务，产出领域原语或值对象（如 ID、令牌），由工厂或构造器调用。
 *
 * @author wizard-lee
 */
public interface ICapabilityProviderService extends IDomainService {
}
