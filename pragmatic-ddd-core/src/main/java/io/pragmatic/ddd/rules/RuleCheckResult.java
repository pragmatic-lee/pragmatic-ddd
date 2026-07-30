package io.pragmatic.ddd.rules;

/**
 * 法则规则校验结果 —— IParamRule 的返回值，携带校验通过/失败状态及用于消息格式化的动态参数。
 *
 * <p>创建方式全部通过静态工厂方法，消除 {@code boolean} 构造函数的语义歧义：</p>
 * <ul>
 *   <li>{@link #pass()} — 校验通过</li>
 *   <li>{@link #fail()} — 校验失败，无动态参数</li>
 *   <li>{@link #fail(Object[])} — 校验失败，携带消息参数</li>
 *   <li>{@link #fail(Object[], boolean)} — 校验失败，可控制是否自动格式化</li>
 * </ul>
 *
 * @author wizard-lee
 */
public class RuleCheckResult {

    private static final Object[] EMPTY_PARAMS = new Object[0];
    private static final RuleCheckResult passResult = new RuleCheckResult(true, EMPTY_PARAMS, true);

    private final boolean isSatisfy;
    private final Object[] params;
    private final boolean enableFormat;

    private RuleCheckResult(boolean isSatisfy, Object[] params, boolean enableFormat) {
        this.isSatisfy = isSatisfy;
        this.params = params;
        this.enableFormat = enableFormat;
    }

    /** 校验通过 */
    public static RuleCheckResult pass() {
        return passResult;
    }

    /** 校验失败，携带动态参数（用于 String.format 格式化违规消息） */
    public static RuleCheckResult fail(Object[] params) {
        return new RuleCheckResult(false, params, true);
    }

    /** 校验失败，无动态参数 */
    public static RuleCheckResult fail() {
        return new RuleCheckResult(false, EMPTY_PARAMS, true);
    }

    /** 校验失败，可控制是否自动格式化消息 */
    public static RuleCheckResult fail(Object[] params, boolean enableFormat) {
        return new RuleCheckResult(false, params, enableFormat);
    }

    /** 是否校验通过。 */
    public boolean isSatisfy() {
        return isSatisfy;
    }

    /** 校验失败时的动态消息参数。 */
    public Object[] getParams() {
        return params;
    }

    /** 是否自动格式化消息参数。 */
    public boolean isAutoFormat() {
        return enableFormat;
    }
}
