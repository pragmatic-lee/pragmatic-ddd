package io.pragmatic.ddd.application.compensation;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 补偿动作静态工厂：为无状态场景提供一行式构造。
 *
 * @author wizard-lee
 */
public final class CompensableActions {

    private CompensableActions() {
    }

    /**
     * 以正向函数与逆向消费函数构造补偿动作。
     *
     * @param actionKey    幂等键
     * @param forward      正向执行函数
     * @param compensation 逆向补偿函数
     * @param <T>          正向产出类型
     * @return 补偿动作
     */
    public static <T> ICompensableAction<T> of(String actionKey,
                                               Supplier<T> forward,
                                               Consumer<T> compensation) {
        return new ICompensableAction<>() {
            @Override
            public String actionKey() {
                return actionKey;
            }

            @Override
            public T execute() {
                return forward.get();
            }

            @Override
            public void compensate(T result) {
                compensation.accept(result);
            }
        };
    }
}
