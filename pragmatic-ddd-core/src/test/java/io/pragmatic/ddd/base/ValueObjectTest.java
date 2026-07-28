package io.pragmatic.ddd.base;

import org.junit.Assert;
import org.junit.Test;

import java.util.Objects;

/**
 * {@link ValueObject} 结构相等性单元测试。
 */
public class ValueObjectTest {

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
    public void reflexivity() {
        Money m = new Money(10, "USD");
        Assert.assertEquals(m, m);
    }

    @Test
    public void symmetry() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, a);
    }

    @Test
    public void transitivity() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        Money c = new Money(10, "USD");
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, c);
        Assert.assertEquals(a, c);
    }

    @Test
    public void consistency() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(a, b);
        }
    }

    @Test
    public void nullIsNotEqual() {
        Money m = new Money(10, "USD");
        Assert.assertNotEquals(m, null);
    }

    @Test
    public void differentTypeIsNotEqual() {
        // 字段结构与 Money 相同，但类型不同 → 不应相等（运行时类型严格判断）
        Money money = new Money(10, "USD");
        Price price = new Price(10, "USD");
        Assert.assertNotEquals(money, price);
    }

    @Test
    public void differentComponentsNotEqual() {
        Assert.assertNotEquals(new Money(10, "USD"), new Money(10, "EUR"));
        Assert.assertNotEquals(new Money(10, "USD"), new Money(20, "USD"));
    }

    @Test
    public void nullComponentSafe() {
        Label a = new Label(null, "desc");
        Label b = new Label(null, "desc");
        Assert.assertEquals(a, b);
        Assert.assertNotEquals(a, new Label("x", "desc"));

        Label c = new Label("name", null);
        Label d = new Label("name", null);
        Assert.assertEquals(c, d);
    }

    // ===================== hashCode 一致性 =====================

    @Test
    public void hashCodeConsistentWithEquals() {
        Money a = new Money(10, "USD");
        Money b = new Money(10, "USD");
        Assert.assertEquals(a.hashCode(), b.hashCode());

        Money c = new Money(20, "USD");
        if (!a.equals(c)) {
            // 不相等不强制 hash 不同，但相等必须 hash 相同；此处仅验证不抛异常
            Assert.assertNotEquals(a, c);
        }
    }

    @Test
    public void toStringContainsComponents() {
        Money m = new Money(10, "USD");
        String s = m.toString();
        Assert.assertTrue(s.contains("Money"));
        Assert.assertTrue(s.contains("10"));
        Assert.assertTrue(s.contains("USD"));
    }

    // ===================== 与实体身份相等的区分 =====================

    @Test
    public void valueObjectVsEntityEqualitySemanticsDiffer() {
        // 值对象：结构相等（属性同即相等），不依赖 ID
        Assert.assertEquals(new Money(10, "USD"), new Money(10, "USD"));

        // 实体（AbstractEntity 子类）：未持久化（ID 为 null）互不相等
        TestEntity e1 = new TestEntity();
        TestEntity e2 = new TestEntity();
        Assert.assertNotEquals(e1, e2);
    }

    static final class TestEntity extends AbstractEntity<Long> {
        // AbstractEntity 已精简为纯数据容器（重构计划 3.2），无需实现规则/操作注册表
    }
}
