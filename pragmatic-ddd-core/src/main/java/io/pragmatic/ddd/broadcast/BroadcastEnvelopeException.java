package io.pragmatic.ddd.broadcast;

/**
 * 对外广播信封处理失败异常（序列化或构造），具有"不可重试"语义（对应 AclConversionException）。
 * 通常源于编程或配置错误，不应对其自动重试。
 *
 * @author wizard-lee
 */
public class BroadcastEnvelopeException extends BroadcastException {

    public BroadcastEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
