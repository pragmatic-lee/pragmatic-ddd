package io.pragmatic.ddd.acl;

/**
 * 外部调用日志钩子。在调用器的关键节点触发，便于业务侧输出日志 / 埋点 / 链路追踪。
 * 所有方法默认空实现，业务侧按需覆盖对应节点即可。不绑定任何具体日志框架。
 *
 * @param <Q> 对方接口入参
 * @param <S> 对方接口返回值
 * @author wizard-lee
 */
public interface ExternalCallLogger<Q, S> {

    /** 空实现，不输出任何内容。 */
    ExternalCallLogger<Object, Object> NOOP = new ExternalCallLogger<>() { };

    /** 类型安全的空实现工厂，便于在泛型调用处推断类型。 */
    @SuppressWarnings("unchecked")
    static <Q, S> ExternalCallLogger<Q, S> noop() {
        return (ExternalCallLogger<Q, S>) NOOP;
    }

    /** 请求转换完成后、调用对方前触发。 */
    default void onRequest(Q request) {
    }

    /** 收到对方响应、响应转换前触发。 */
    default void onResponse(S response) {
    }

    /** 调用对方（或转换）发生异常时触发。 */
    default void onError(Throwable ex) {
    }
}
