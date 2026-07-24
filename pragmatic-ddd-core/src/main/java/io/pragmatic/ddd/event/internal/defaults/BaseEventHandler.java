package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IEventRegistry;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.IHandle;

public abstract class BaseEventHandler<T extends IDomainEvent> {

    private final Class<T> cls;
    private IExecuteCondition<T> condition;
    private String dependSubscriber = "";
    private DeliveryPolicy policy = DeliveryPolicy.IMMEDIATE;

    public BaseEventHandler(Class<T> cls) {
        this.cls = cls;
    }

    protected abstract void handle(T event);

    protected ExecuteStatus runCondition(T event) {
        return ExecuteStatus.EXECUTE;
    }

    /** 流式设置执行条件(可选);未设置时回退到 runCondition() 方法。 */
    public BaseEventHandler<T> withCondition(IExecuteCondition<T> condition) {
        this.condition = condition;
        return this;
    }

    /** 流式设置依赖订阅者(可选)。 */
    public BaseEventHandler<T> dependsOn(String dependSubscriber) {
        this.dependSubscriber = dependSubscriber;
        return this;
    }

    /** 流式设置投递策略(可选)。 */
    public BaseEventHandler<T> withPolicy(DeliveryPolicy policy) {
        this.policy = policy;
        return this;
    }

    /** 适应新注册 API:用 cls + handle + 内部元数据 一次性注册。 */
    public void register(IEventRegistry registry, String alias) {
        IExecuteCondition<T> cond = condition != null
                ? condition
                : SubscriberFactory.buildCondition(cls, this::runCondition);
        registry.registerSubscriber(alias, cls, this::handle, cond, dependSubscriber, policy);
    }
}
