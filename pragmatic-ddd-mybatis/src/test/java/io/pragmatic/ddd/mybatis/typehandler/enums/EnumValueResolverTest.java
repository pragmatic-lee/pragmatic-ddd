package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EnumValueResolver 的纯单测：规则解析、四类解析策略与兜底懒注册。
 *
 * @author wizard-lee
 */
@DisplayName("EnumValueResolver 解析规则")
class EnumValueResolverTest {

    enum StatusEnum implements IEnumValue<Integer, StatusEnum> {
        NORMAL(1, "正常"),
        DISABLED(2, "禁用"),
        PENDING(3, "待处理");

        private final Integer code;
        private final String label;

        StatusEnum(Integer code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    enum CodeEnum implements IEnumValue<String, CodeEnum> {
        A("a", "A标签"),
        B("b", "B标签");

        private final String code;
        private final String label;

        CodeEnum(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    @Nested
    @DisplayName("规则解析")
    class RuleResolution {

        @Test
        @DisplayName("默认实例的默认规则为 CODE")
        void defaultRuleIsCode() {
            EnumValueResolver resolver = new EnumValueResolver();

            assertThat(resolver.ruleOf(StatusEnum.class)).isEqualTo(EnumRule.CODE);
        }

        @Test
        @DisplayName("显式指定默认规则后生效")
        void explicitDefaultRule() {
            EnumValueResolver resolver = new EnumValueResolver(new DefaultEnumCodec(), EnumRule.NAME);

            assertThat(resolver.ruleOf(StatusEnum.class)).isEqualTo(EnumRule.NAME);
        }

        @Test
        @DisplayName("register 显式策略后 ruleOf 返回该策略")
        void registerExplicitRule() {
            EnumValueResolver resolver = new EnumValueResolver();
            resolver.register(StatusEnum.class, EnumRule.LABEL);

            assertThat(resolver.ruleOf(StatusEnum.class)).isEqualTo(EnumRule.LABEL);
        }
    }

    @Nested
    @DisplayName("四类解析策略")
    class ResolveStrategies {

        private final EnumValueResolver resolver = new EnumValueResolver();

        @Test
        @DisplayName("byValue 按枚举值命中")
        void byValue() {
            assertThat(resolver.byValue(StatusEnum.class, 1)).isEqualTo(StatusEnum.NORMAL);
            assertThat(resolver.byValue(StatusEnum.class, 2)).isEqualTo(StatusEnum.DISABLED);
        }

        @Test
        @DisplayName("byValue 命中不存在的值时抛异常")
        void byValueMissingThrows() {
            assertThatThrownBy(() -> resolver.byValue(StatusEnum.class, 99))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("byName 按枚举常量名命中")
        void byName() {
            assertThat(resolver.byName(StatusEnum.class, "PENDING")).isEqualTo(StatusEnum.PENDING);
        }

        @Test
        @DisplayName("byName 命中不存在的常量名时抛异常")
        void byNameMissingThrows() {
            assertThatThrownBy(() -> resolver.byName(StatusEnum.class, "NOT_EXIST"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("byOrdinal 按下标命中")
        void byOrdinal() {
            assertThat(resolver.byOrdinal(StatusEnum.class, 0)).isEqualTo(StatusEnum.NORMAL);
            assertThat(resolver.byOrdinal(StatusEnum.class, 2)).isEqualTo(StatusEnum.PENDING);
        }

        @Test
        @DisplayName("byOrdinal 越界时抛异常")
        void byOrdinalOutOfRangeThrows() {
            assertThatThrownBy(() -> resolver.byOrdinal(StatusEnum.class, 99))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("resolve 按 CODE 解析（默认规则，按枚举值查）")
        void resolveByCode() {
            assertThat(resolver.resolve(StatusEnum.class, 1)).isEqualTo(StatusEnum.NORMAL);
        }

        @Test
        @DisplayName("resolve 字符串按 NAME 解析")
        void resolveByName() {
            assertThat(resolver.resolve(StatusEnum.class, "DISABLED", EnumRule.NAME)).isEqualTo(StatusEnum.DISABLED);
        }

        @Test
        @DisplayName("resolve 字符串按 ORDINAL 解析")
        void resolveByOrdinal() {
            assertThat(resolver.resolve(StatusEnum.class, 2, EnumRule.ORDINAL)).isEqualTo(StatusEnum.PENDING);
        }

        @Test
        @DisplayName("未预注册的类在首次 resolve 时兜底懒注册")
        void resolveLazilyRegistersUnknownClass() {
            assertThat(resolver.resolve(CodeEnum.class, "a")).isEqualTo(CodeEnum.A);
        }

        @Test
        @DisplayName("resolve 非法输入抛异常")
        void resolveInvalidThrows() {
            assertThatThrownBy(() -> resolver.resolve(StatusEnum.class, "???"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
