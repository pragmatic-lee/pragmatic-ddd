package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * 补偿动作静态工厂：为无状态场景提供一行式构造。
 *
 * @author wizard-lee
 */
public final class CompensableActions {

    private CompensableActions() {
    }

    /**
     * 以需求类型、聚合类型与正向 / 回写 / 逆向函数构造补偿动作（无状态场景一行式）。
     *
     * @param requirementType 需求类型（IExternalRequirement 的具体 record 类型）
     * @param aggregateType   适用聚合类型
     * @param forwardFn       正向执行函数
     * @param applyFn         回写函数
     * @param compensateFn    逆向补偿函数
     * @param <A>             聚合类型
     * @param <REQ>           需求类型
     * @param <T>             正向产出类型
     * @return 补偿动作
     */
    public static <A extends AggregateRoot<?>, REQ extends IExternalRequirement, T> ICompensableAction<A, REQ, T> of(
            Class<REQ> requirementType,
            Class<A> aggregateType,
            BiFunction<A, REQ, T> forwardFn,
            BiConsumer<A, T> applyFn,
            BiConsumer<A, T> compensateFn) {
        return new ICompensableAction<>() {
            @Override
            public Class<REQ> requirementType() {
                return requirementType;
            }

            @Override
            public Class<A> aggregateType() {
                return aggregateType;
            }

            @Override
            public T execute(A aggregateRoot, REQ requirement) {
                return forwardFn.apply(aggregateRoot, requirement);
            }

            @Override
            public void apply(A aggregateRoot, T result) {
                applyFn.accept(aggregateRoot, result);
            }

            @Override
            public void compensate(A aggregateRoot, T result) {
                compensateFn.accept(aggregateRoot, result);
            }
        };
    }
}
