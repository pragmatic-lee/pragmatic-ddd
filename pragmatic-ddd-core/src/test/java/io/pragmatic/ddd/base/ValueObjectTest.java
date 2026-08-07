package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.7：ValueObject（值对象）结构相等性单元测试（迁 JUnit5 + AssertJ）。
  * @author wizard-lee
 */
class ValueObjectTest {

    // ===================== 测试夹具 =====================

    /** 简单值对象：金额 + 币种 */
    static final class Money extends ValueObject {
        private final long amount;
        private final String currency;

        Money(long amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        @Override
        protected Object[] equalityComponents() {
            return new Object[]{amount, currency};
        }
    }

    /** 含 null 成分的值对象，验证 Arrays.equals 对 null 安全 */
    static final class Label extends ValueObject {
        private final String name;
        private final String desc;

        Label(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        @Override
        protected Object[] equalityComponents() {
            return new Object[]{name, desc};
        }
    }

    /** 不同类型的值对象，验证类型严格判断 */
    static final class Price extends ValueObject {
        private final long amount;
        private final String currency;

        Price(long amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        @Override
        protected Object[] equalityComponents() {
            return new Object[]{amount, currency};
        }
    }

    // ===================== 相等性契约 =====================

    @Test
    void reflexivity() {
        Money m = new Money(10, "USD");
        assertThat(m).isEqualTo(m);
    }

    @Test
    void symmetry() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void transitivity() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        Money c = new Money(10, "USD");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(c);
        assertThat(a).isEqualTo(c);
    }

    @Test
    void consistency() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        for (int i = 0; i < 5; i++) {
            assertThat(a).isEqualTo(b);
        }
    }

    @Test
    void nullIsNotEqual() {
        Money m = new Money(10, "USD");
        assertThat(m).isNotEqualTo(null);
    }

    @Test
    void differentTypeIsNotEqual() {
        Money money = new Money(10, "USD");
        Price price = new Price(10, "USD");
        assertThat(money).isNotEqualTo(price);
    }

    @Test
    void differentComponentsNotEqual() {
        assertThat(new Money(10, "USD")).isNotEqualTo(new Money(10, "EUR"));
        assertThat(new Money(10, "USD")).isNotEqualTo(new Money(20, "USD"));
    }

    @Test
    void nullComponentSafe() {
        Label a = new Label(null, "desc");
        Label b = new Label(null, "desc");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(new Label("x", "desc"));

        Label c = new Label("name", null);
        Label d = new Label("name", null);
        assertThat(c).isEqualTo(d);
    }

    // ===================== 边界：空成分 =====================

    @Test
    void emptyComponents_sameTypeEqual() {
        assertThat(new EmptyVo()).isEqualTo(new EmptyVo());
    }

    static final class EmptyVo extends ValueObject {
        @Override
        protected Object[] equalityComponents() {
            return new Object[]{};
        }
    }

    // ===================== hashCode 一致性 =====================

    @Test
    void hashCodeConsistentWithEquals() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        Money c = new Money(20, "USD");
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isNotEqualTo(c.hashCode());
    }

    @Test
    void toStringContainsComponents() {
        Money m = new Money(10, "USD");
        String s = m.toString();
        assertThat(s).contains("Money").contains("10").contains("USD");
    }

    // ===================== 与实体身份相等的区分 =====================

    @Test
    void valueObjectVsEntityEqualitySemanticsDiffer() {
        assertThat(new Money(10, "USD")).isEqualTo(new Money(10, "USD"));

        TestEntity e1 = new TestEntity();
        TestEntity e2 = new TestEntity();
        assertThat(e1).isNotEqualTo(e2);
    }

    static final class TestEntity extends AbstractEntity<Long> {
    }
}
