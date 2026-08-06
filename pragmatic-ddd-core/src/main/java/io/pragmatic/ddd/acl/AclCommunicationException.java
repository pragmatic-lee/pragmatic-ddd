package io.pragmatic.ddd.acl;

/**
 * 防腐层外部通信异常（网络、超时、远程业务错误、非预期状态码等）。
 * 通信失败通常可由上层决策重试、降级或熔断。
 *
 * @author wizard-lee
 */
public class AclCommunicationException extends AclException {

    public AclCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AclCommunicationException(Throwable cause) {
        super(cause);
    }
}
