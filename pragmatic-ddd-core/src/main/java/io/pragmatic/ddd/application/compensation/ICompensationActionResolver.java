package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

/**
 * 外部需求解析器：把聚合声明的一项外部需求解析为待执行补偿命令。
 *
 * @author wizard-lee
 */
public interface ICompensationActionResolver {

    /**
     * 解析一项外部需求。
     *
     * @param requirement   外部需求（强类型 record 实例）
     * @param aggregateRoot 声明该需求的聚合
     * @param <A>           聚合类型
     * @param <R>           正向产出类型
     * @return 待执行补偿命令；无匹配动作或聚合类型不匹配时显式失败
     */
    <A extends AggregateRoot<?>, R> CompensationCommand<A, ?, R> resolve(
            IExternalRequirement requirement, A aggregateRoot);
}
