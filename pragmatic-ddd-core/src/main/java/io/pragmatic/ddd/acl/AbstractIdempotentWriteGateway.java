package io.pragmatic.ddd.acl;

import java.util.Optional;

/**
 * 写入前的"先查后写"套路抽象类（继承式，幂等保护）。
 * 套路：用唯一键查询对方 → 已存在则短路返回；否则走写入。
 * 注意：本套路仅降低重复概率，真正幂等仍需对方写入接口按唯一键去重。
 *
 * @param <P> 领域入参
 * @param <R> 领域返回值
 * @param <Q> 对方接口入参
 * @param <S> 对方接口返回值
 * @param <K> 查重唯一键
 * @author wizard-lee
 */
public abstract class AbstractIdempotentWriteGateway<P, R, Q, S, K> {

    /** 日志钩子，默认空实现（不输出）。 */
    @SuppressWarnings("unchecked")
    protected ExternalCallLogger<Q, S> logger = (ExternalCallLogger<Q, S>) ExternalCallLogger.NOOP;

    /** 设置日志钩子，便于在转换 / 调用节点输出日志或埋点。 */
    public final void setLogger(ExternalCallLogger<Q, S> logger) {
        this.logger = logger;
    }

    /** 从领域入参提取查重唯一键。 */
    protected abstract K uniqueKey(P param);

    /** 用唯一键查询对方，返回 Optional（空表示未处理过）。 */
    protected abstract Optional<S> queryByKey(K key);

    /** 将已存在的对方记录转换为领域返回值（需结合状态判断是否为终态成功）。 */
    protected abstract R toDomainResultFromExisting(S existing);

    /** 领域入参 → 对方接口入参。 */
    protected abstract Q toExternalRequest(P param);

    /** 调用对方写入接口。 */
    protected abstract S doWrite(Q request);

    /** 对方返回值 → 领域返回值。 */
    protected abstract R toDomainResult(S response);

    /** 模板方法：先查后写，已存在则短路返回。 */
    public final R write(P param) {
        K key = uniqueKey(param);
        Optional<S> existing = queryByKey(key);
        if (existing.isPresent()) {
            logger.onResponse(existing.get());
            return toDomainResultFromExisting(existing.get());
        }
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
