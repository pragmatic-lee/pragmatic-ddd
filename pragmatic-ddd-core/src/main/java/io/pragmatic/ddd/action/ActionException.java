package io.pragmatic.ddd.action;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * @author lixiaojing10
 */
public class ActionException extends PragmaticException {
    public ActionException(String message) {
        super(message);
    }
}
