package io.pragmatic.ddd.base.fixture;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

/**
 * 规则消息注册表夹具：提供若干可复用的 {@link MessageCode} 常量。
 */
public final class SampleMessages extends BrokenRuleRegistry {

    public static final MessageCode NAME_ERROR = MessageCode.of("NAME_ERROR", "名称:%s 不能为空");
    public static final MessageCode AGE_ERROR = MessageCode.of("AGE_ERROR", "年龄不合法");
    public static final MessageCode FORMAT_ERROR = MessageCode.of("FORMAT_ERROR", "值=%s不合法");

    public static final SampleMessages INSTANCE = new SampleMessages();
}
