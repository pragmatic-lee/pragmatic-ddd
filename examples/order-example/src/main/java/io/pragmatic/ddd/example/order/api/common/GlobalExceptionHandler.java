package io.pragmatic.ddd.example.order.api.common;

import io.pragmatic.ddd.base.BrokenRuleAggregateException;
import io.pragmatic.ddd.base.BrokenRuleException;
import io.pragmatic.ddd.base.RuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：将接口层与领域层异常统一翻译为 Result 响应。
 *
 * @author wizard-lee
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 接口层业务异常。 */
    @ExceptionHandler(ApiException.class)
    public Result<Void> handleApiException(ApiException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /** 领域规则校验失败（订单状态机等），取规则文案作为提示。 */
    @ExceptionHandler(RuleException.class)
    public Result<Void> handleRuleException(RuleException ex) {
        return Result.fail(ApiErrorCode.ORDER_STATE_ERROR, resolveRuleMessage(ex));
    }

    /** 请求体格式错误、参数类型不匹配等。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public Result<Void> handleBadRequest(Exception ex) {
        log.warn("请求参数不合法", ex);
        return Result.fail(ApiErrorCode.BAD_REQUEST, "请求参数不合法");
    }

    /** 非法参数（分页越界、数值解析失败等）。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("请求参数不合法", ex);
        return Result.fail(ApiErrorCode.BAD_REQUEST, "请求参数不合法");
    }

    /** 兜底异常。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.fail(ApiErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
    }

    private String resolveRuleMessage(RuleException ex) {
        if (ex instanceof BrokenRuleAggregateException aggregate) {
            String joined = aggregate.getExceptions().stream()
                    .map(BrokenRuleException::getMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .collect(Collectors.joining("；"));
            return joined.isBlank() ? "订单状态不允许该操作" : joined;
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "订单状态不允许该操作";
        }
        return message;
    }
}
