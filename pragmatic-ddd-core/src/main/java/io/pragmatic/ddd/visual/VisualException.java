package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 可视化过程异常 —— 继承框架统一异常，承载解析或渲染失败原因。
 *
 * @author wizard-lee
 */
public class VisualException extends PragmaticException {

    /** 由底层异常包装构造。 */
    public VisualException(Throwable cause) {
        super(cause);
    }

    /** 由错误消息构造。 */
    public VisualException(String message) {
        super(message);
    }

    /** 由错误消息与底层异常共同构造。 */
    public VisualException(String message, Throwable cause) {
        super(message, cause);
    }
}
