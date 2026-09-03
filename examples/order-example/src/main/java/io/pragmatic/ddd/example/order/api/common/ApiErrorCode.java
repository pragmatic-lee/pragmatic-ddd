package io.pragmatic.ddd.example.order.api.common;

/**
 * 接口错误码（对齐 order-backend-api-design.md 7.1 草案）：
 * 0 成功；1xxx 参数/资源校验；2xxx 订单业务规则；9xxx 系统异常。
 *
 * @author wizard-lee
 */
public final class ApiErrorCode {

    /** 成功。 */
    public static final int SUCCESS = 0;

    /** 请求参数不合法。 */
    public static final int BAD_REQUEST = 1001;

    /** 订单不存在。 */
    public static final int ORDER_NOT_FOUND = 1002;

    /** 订单业务规则校验失败（具体原因见 message）。 */
    public static final int ORDER_STATE_ERROR = 2001;

    /** 系统繁忙。 */
    public static final int SYSTEM_ERROR = 9001;

    private ApiErrorCode() {
    }
}
