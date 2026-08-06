package io.pragmatic.ddd.config;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 配置绑定失败异常。
 *
 * @author wizard-lee
 */
public class ConfigurationBindingException extends PragmaticException {

    /** 以消息构造。 */
    public ConfigurationBindingException(String message) {
        super(message);
    }

    /** 以消息与原因构造。 */
    public ConfigurationBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
