package io.pragmatic.ddd.example.order.api.common;

import lombok.Getter;

/**
 * 统一响应体（对齐 api-contract.md 1.1）：code=0 成功，非 0 为业务失败，message 为可直接展示的文案。
 *
 * @param <T> 业务数据类型
 * @author wizard-lee
 */
@Getter
public final class Result<T> {

    private final int code;

    private final T data;

    private final String message;

    private Result(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    /** 成功响应。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ApiErrorCode.SUCCESS, data, null);
    }

    /** 失败响应。 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, null, message);
    }
}
