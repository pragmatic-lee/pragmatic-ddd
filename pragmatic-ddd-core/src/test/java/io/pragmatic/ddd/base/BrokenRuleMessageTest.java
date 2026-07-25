package io.pragmatic.ddd.base;

import org.junit.Assert;
import org.junit.Test;

/**
 * 对应设计文档阶段 6.2：BrokenRuleMessage（抽象基类 + 反射自动注册）单元测试。
 */
public class BrokenRuleMessageTest {

    public static class SampleBrokenRuleMessage extends BrokenRuleMessage {
        public static final MessageCode NAME_ERROR = MessageCode.of("NAME_ERROR", "名称不能为空");
        public static final MessageCode AGE_ERROR = MessageCode.of("AGE_ERROR", "年龄不合法");
        public static final MessageCode FORMAT_ERROR = MessageCode.of("FORMAT_ERROR", "值=%s不合法");
        // 干扰项：非 MessageCode 静态字段，反射注册应忽略
        public static final String VERSION = "1.0";
        public static final int MAX = 10;
        // INSTANCE 自引用字段（类型非 MessageCode，不应被误注册）
        public static final SampleBrokenRuleMessage INSTANCE = new SampleBrokenRuleMessage();
    }

    @Test
    public void constructor_reflection_auto_register() {
        SampleBrokenRuleMessage message = SampleBrokenRuleMessage.INSTANCE;
        Assert.assertEquals("名称不能为空", message.getRuleDescription("NAME_ERROR"));
        Assert.assertEquals("年龄不合法", message.getRuleDescription("AGE_ERROR"));
    }

    @Test
    public void unregistered_key_returns_empty() {
        SampleBrokenRuleMessage message = SampleBrokenRuleMessage.INSTANCE;
        Assert.assertEquals("", message.getRuleDescription("NOT_EXIST"));
    }

    @Test
    public void nonMessageCode_static_field_ignored() {
        SampleBrokenRuleMessage message = SampleBrokenRuleMessage.INSTANCE;
        Assert.assertEquals("", message.getRuleDescription("VERSION"));
        Assert.assertEquals("", message.getRuleDescription("MAX"));
    }

    @Test
    public void instance_self_reference_not_registered() {
        SampleBrokenRuleMessage message = SampleBrokenRuleMessage.INSTANCE;
        Assert.assertEquals("", message.getRuleDescription("INSTANCE"));
    }

    @Test
    public void createException_carries_key_and_message() {
        SampleBrokenRuleMessage message = SampleBrokenRuleMessage.INSTANCE;
        BrokenRuleException ex = message.createException("NAME_ERROR");
        Assert.assertEquals("NAME_ERROR", ex.getCode());
        Assert.assertEquals("名称不能为空", ex.getMessage());
    }

    @Test
    public void createExceptionWithParam_varargs_format() {
        SampleBrokenRuleMessage message = SampleBrokenRuleMessage.INSTANCE;
        BrokenRuleException ex = message.createExceptionWithParam("FORMAT_ERROR", 18);
        Assert.assertEquals("值=18不合法", ex.getMessage());
    }

    @Test
    public void of_factory_inline() {
        BrokenRuleMessage message = BrokenRuleMessage.of(
                MessageCode.of("X1", "x1"), MessageCode.of("X2", "x2"));
        Assert.assertEquals("x1", message.getRuleDescription("X1"));
        Assert.assertEquals("x2", message.getRuleDescription("X2"));
    }

    @Test
    public void manual_register_via_subclass() {
        BrokenRuleMessage message = new BrokenRuleMessage() {{
            register(MessageCode.of("MANUAL", "手动注册"));
        }};
        Assert.assertEquals("手动注册", message.getRuleDescription("MANUAL"));
    }
}
