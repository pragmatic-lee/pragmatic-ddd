package io.pragmatic.ddd.acl;

/**
 * 与外部应用交互的写入套路抽象类（继承式）。
 * 套路：领域入参 → 请求转换 → 调用对方写入 → 响应转换 → 领域返回值。
 * 通信异常应向上抛出由上层重试；本地转换异常不应被误判为可重试写超时。
 *
 * @param <P> 领域入参
 * @param <R> 领域返回值
 * @param <Q> 对方接口入参
 * @param <S> 对方接口返回值
 * @author wizard-lee
 */
public abstract class AbstractWriteGateway<P, R, Q, S> {

    /** 日志钩子，默认空实现（不输出）。 */
    @SuppressWarnings("unchecked")
    protected ExternalCallLogger<Q, S> logger = (ExternalCallLogger<Q, S>) ExternalCallLogger.NOOP;

    /** 设置日志钩子，便于在转换 / 调用节点输出日志或埋点。 */
    public final void setLogger(ExternalCallLogger<Q, S> logger) {
        this.logger = logger;
    }

    /** 领域入参 → 对方接口入参。 */
    protected abstract Q toExternalRequest(P param);

    /** 调用对方写入接口。 */
    protected abstract S doWrite(Q request);

    /** 对方返回值 → 领域返回值。 */
    protected abstract R toDomainResult(S response);

    /** 模板方法：执行一次写入。 */
    public final R write(P param) {
        Q request = toExternalRequest(param);
        logger.onRequest(request);
        S response;
        try {
            response = doWrite(request);
        } catch (RuntimeException ex) {
            logger.onError(ex);
            throw ex;
        }
        logger.onResponse(response);
        return toDomainResult(response);
    }
}
