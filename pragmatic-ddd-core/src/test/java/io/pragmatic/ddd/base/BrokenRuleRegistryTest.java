package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档阶段 6.3：BrokenRuleRegistry（抽象基类 + 反射自动注册）单元测试。
 * 合并原 BrokenRuleMessageTest 与 BrokenRuleRefactorTest 中注册表相关用例，去重。
  * @author wizard-lee
 */
class BrokenRuleRegistryTest {

    static class SampleRegistry extends BrokenRuleRegistry {
        public static final MessageCode NAME_ERROR = MessageCode.of("NAME_ERROR", "名称不能为空");
        public static final MessageCode AGE_ERROR = MessageCode.of("AGE_ERROR", "年龄不合法");
        public static final MessageCode FORMAT_ERROR = MessageCode.of("FORMAT_ERROR", "值=%s不合法");
        // 干扰项：非 MessageCode 静态字段，反射注册应忽略
        public static final String VERSION = "1.0";
        public static final int MAX = 10;
        // INSTANCE 自引用字段（类型非 MessageCode，不应被误注册）
        public static final SampleRegistry INSTANCE = new SampleRegistry();
    }

    @Test
    void constructor_reflectionAutoRegister() {
        SampleRegistry r = SampleRegistry.INSTANCE;
        assertThat(r.getRuleDescription("NAME_ERROR")).isEqualTo("名称不能为空");
        assertThat(r.getRuleDescription("AGE_ERROR")).isEqualTo("年龄不合法");
    }

    @Test
    void unregisteredKey_returnsEmpty() {
        assertThat(SampleRegistry.INSTANCE.getRuleDescription("NOT_EXIST")).isEmpty();
    }

    @Test
    void nonMessageCodeStaticField_ignored() {
        assertThat(SampleRegistry.INSTANCE.getRuleDescription("VERSION")).isEmpty();
        assertThat(SampleRegistry.INSTANCE.getRuleDescription("MAX")).isEmpty();
    }

    @Test
    void instanceSelfReference_notRegistered() {
        assertThat(SampleRegistry.INSTANCE.getRuleDescription("INSTANCE")).isEmpty();
    }

    @Test
    void ofInlineFactory_registers() {
        BrokenRuleRegistry r = BrokenRuleRegistry.of(
                MessageCode.of("X1", "x1"), MessageCode.of("X2", "x2"));
        assertThat(r.getRuleDescription("X1")).isEqualTo("x1");
        assertThat(r.getRuleDescription("X2")).isEqualTo("x2");
    }

    @Test
    void ofEmpty_returnsEmptyRegistry() {
        BrokenRuleRegistry r = BrokenRuleRegistry.of();
        assertThat(r.getRuleDescription("ANY")).isEmpty();
        assertThat(r.brokenRules()).isEmpty();
    }

    @Test
    void manualRegister_viaSubclass() {
        BrokenRuleRegistry r = new BrokenRuleRegistry() {{
            register(MessageCode.of("MANUAL", "手动注册"));
        }};
        assertThat(r.getRuleDescription("MANUAL")).isEqualTo("手动注册");
    }

    @Test
    void createException_carriesKeyAndMessage() {
        BrokenRuleException ex = SampleRegistry.INSTANCE.createException("NAME_ERROR");
        assertThat(ex.getCode()).isEqualTo("NAME_ERROR");
        assertThat(ex.getMessage()).isEqualTo("名称不能为空");
    }

    @Test
    void createExceptionWithParam_formats() {
        BrokenRuleException ex = SampleRegistry.INSTANCE.createExceptionWithParam("FORMAT_ERROR", 18);
        assertThat(ex.getMessage()).isEqualTo("值=18不合法");
    }

    @Test
    void brokenRules_view_immutable() {
        Map<String, MessageCode> view = SampleRegistry.INSTANCE.brokenRules();
        assertThat(view).containsKeys("NAME_ERROR", "AGE_ERROR", "FORMAT_ERROR");
        assertThatThrownBy(() -> view.put("X", MessageCode.of("X", "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void duplicateLocalCode_lastWins() {
        BrokenRuleRegistry r = new BrokenRuleRegistry() {{
            register(MessageCode.of("DUP", "first"));
            register(MessageCode.of("DUP", "second"));
        }};
        assertThat(r.getRuleDescription("DUP")).isEqualTo("second");
    }
}
