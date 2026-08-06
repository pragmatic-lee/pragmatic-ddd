package io.pragmatic.ddd.broadcast;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 对外广播能力的异常抽象基类，挂在框架统一异常根基 PragmaticException 之下。
 * 可通过 catch (PragmaticException e) 统一兜底捕获。
 *
 * @author wizard-lee
 */
public abstract class BroadcastException extends PragmaticException {

    protected BroadcastException(String message, Throwable cause) {
        super(message, cause);
    }
}
