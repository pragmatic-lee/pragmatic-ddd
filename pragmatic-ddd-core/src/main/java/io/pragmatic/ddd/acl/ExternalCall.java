package io.pragmatic.ddd.acl;

import java.util.Optional;
import java.util.function.Function;

/**
 * 与外部应用交互的固定套路调用器。
 * 套路统一为：领域入参 → 请求转换 → 调用对方 → 响应转换 → 领域返回值。
 * 以静态方法 + 函数参数提供，由防腐层适配器以组合方式调用（不要求继承）。
 *
 * @author wizard-lee
 */
public final class ExternalCall {

    private ExternalCall() {
    }

    /**
     * 查询套路（无副作用，调用失败可由上层直接重试）。
     *
     * @param param     领域入参
     * @param toRequest 领域入参 → 对方接口入参
     * @param doCall    调用对方查询接口
     * @param toResult  对方返回值 → 领域返回值
     * @param <P>       领域入参
     * @param <R>       领域返回值
     * @param <Q>       对方接口入参
     * @param <S>       对方接口返回值
     * @return 领域返回值
     */
    public static <P, R, Q, S> R query(P param,
                                       Function<P, Q> toRequest,
                                       Function<Q, S> doCall,
                                       Function<S, R> toResult) {
        return query(param, toRequest, doCall, toResult, ExternalCallLogger.noop());
    }

    public static <P, R, Q, S> R query(P param,
                                       Function<P, Q> toRequest,
                                       Function<Q, S> doCall,
                                       Function<S, R> toResult,
                                       ExternalCallLogger<Q, S> logger) {
        Q request = toRequest.apply(param);
        logger.onRequest(request);
        S response;
        try {
            response = doCall.apply(request);
        } catch (RuntimeException ex) {
            logger.onError(ex);
            throw ex;
        }
        logger.onResponse(response);
        return toResult.apply(response);
    }

    /**
     * 写入套路（有副作用）。通信异常应向上抛出由上层重试；
     * 本地转换异常不应被误判为可重试写超时。
     *
     * @param param     领域入参
     * @param toRequest 领域入参 → 对方接口入参
     * @param doCall    调用对方写入接口
     * @param toResult  对方返回值 → 领域返回值
     * @param <P>       领域入参
     * @param <R>       领域返回值
     * @param <Q>       对方接口入参
     * @param <S>       对方接口返回值
     * @return 领域返回值
     */
    public static <P, R, Q, S> R write(P param,
                                       Function<P, Q> toRequest,
                                       Function<Q, S> doCall,
                                       Function<S, R> toResult) {
        return write(param, toRequest, doCall, toResult, ExternalCallLogger.noop());
    }

    public static <P, R, Q, S> R write(P param,
                                       Function<P, Q> toRequest,
                                       Function<Q, S> doCall,
                                       Function<S, R> toResult,
                                       ExternalCallLogger<Q, S> logger) {
        Q request = toRequest.apply(param);
        logger.onRequest(request);
        S response;
        try {
            response = doCall.apply(request);
        } catch (RuntimeException ex) {
            logger.onError(ex);
            throw ex;
        }
        logger.onResponse(response);
        return toResult.apply(response);
    }

    /**
     * 先查后写套路（幂等保护）：用唯一键查询对方，已存在则短路返回；否则写入。
     * 注意：本套路仅降低重复概率，真正幂等仍需对方写入接口按唯一键去重。
     *
     * @param param           领域入参
     * @param toKey           从领域入参提取查重唯一键
     * @param queryByKey      用唯一键查询对方，返回 Optional（空表示未处理过）
     * @param toResultExisting 将已存在的对方记录转换为领域返回值
     * @param toRequest       领域入参 → 对方接口入参
     * @param doCall          调用对方写入接口
     * @param toResult        对方返回值 → 领域返回值
     * @param <P>             领域入参
     * @param <R>             领域返回值
     * @param <Q>             对方接口入参
     * @param <S>             对方接口返回值
     * @param <K>             查重唯一键
     * @return 领域返回值
     */
    public static <P, R, Q, S, K> R writeIdempotent(P param,
                                                    Function<P, K> toKey,
                                                    Function<K, Optional<S>> queryByKey,
                                                    Function<S, R> toResultExisting,
                                                    Function<P, Q> toRequest,
                                                    Function<Q, S> doCall,
                                                    Function<S, R> toResult) {
        return writeIdempotent(param, toKey, queryByKey, toResultExisting, toRequest, doCall, toResult, ExternalCallLogger.noop());
    }

    public static <P, R, Q, S, K> R writeIdempotent(P param,
                                                    Function<P, K> toKey,
                                                    Function<K, Optional<S>> queryByKey,
                                                    Function<S, R> toResultExisting,
                                                    Function<P, Q> toRequest,
                                                    Function<Q, S> doCall,
                                                    Function<S, R> toResult,
                                                    ExternalCallLogger<Q, S> logger) {
        K key = toKey.apply(param);
        Optional<S> existing = queryByKey.apply(key);
        if (existing.isPresent()) {
            logger.onResponse(existing.get());
            return toResultExisting.apply(existing.get());
        }
        Q request = toRequest.apply(param);
        logger.onRequest(request);
        S response;
        try {
            response = doCall.apply(request);
        } catch (RuntimeException ex) {
            logger.onError(ex);
            throw ex;
        }
        logger.onResponse(response);
        return toResult.apply(response);
    }
}
