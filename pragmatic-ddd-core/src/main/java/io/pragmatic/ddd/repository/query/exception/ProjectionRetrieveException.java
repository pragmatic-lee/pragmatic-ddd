package io.pragmatic.ddd.repository.query.exception;

/**
 * 投影检索失败异常：存储通信超时、远程错误、结果反序列化失败等。
 * 对应 AclCommunicationException / BroadcastSendException 的"可重试"语义，
 * 上层可据此决策重试、降级或熔断。
 *
 * @author wizard-lee
 */
public class ProjectionRetrieveException extends ProjectionException {

    public ProjectionRetrieveException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionRetrieveException(String message) {
        super(message);
    }
}
