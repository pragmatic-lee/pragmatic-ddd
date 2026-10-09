package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.IExternalRequirement;

/**
 * 测试用外部需求：仅携带需求编码，用于构造补偿命令。
 * record 组件 code 直接实现 {@link IExternalRequirement#code()}。
 *
 * @param code 需求编码
 * @author wizard-lee
 */
public record TestRequirement(String code) implements IExternalRequirement {
}
