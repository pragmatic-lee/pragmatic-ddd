package io.pragmatic.ddd.example.order.api.common;

import lombok.Getter;

/**
 * 接口层业务异常：携带错误码与可展示文案，由全局异常处理器翻译为统一响应。
 *
 * @author wizard-lee
 */
@Getter
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }
}
