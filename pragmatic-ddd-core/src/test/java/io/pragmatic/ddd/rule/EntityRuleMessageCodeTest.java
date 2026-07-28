package io.pragmatic.ddd.rule;

import io.pragmatic.ddd.base.BrokenRule;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.MessageCode;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.RulePosition;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static io.pragmatic.ddd.rule.EntityRuleMessageCodeTest.SampleBrokenRuleRegistry.*;

/**
 * 对应设计文档阶段 6.4：EntityRule 全 MessageCode 化补充用例。
 */
public class EntityRuleMessageCodeTest {

    public static class SampleBrokenRuleRegistry extends BrokenRuleRegistry {
        public static final MessageCode R1 = MessageCode.of("R1", "规则1");
        public static final MessageCode R2 = MessageCode.of("R2", "规则2");
        public static final MessageCode R3 = MessageCode.of("R3", "规则3");
        public static final SampleBrokenRuleRegistry INSTANCE = new SampleBrokenRuleRegistry();
    }

    static class SampleEntity extends AggregateRoot<Long> {
        private boolean b1;
        private boolean b2;
        private boolean b3;

        @Override
        protected BrokenRuleRegistry brokenRuleRegistry() {
            return SampleBrokenRuleRegistry.INSTANCE;
        }

        @Override
        protected OperationRegistry operationRegistry() {
            return null;
        }

        public boolean isB1() { return b1; }
        public void setB1(boolean b1) { this.b1 = b1; }
        public boolean isB2() { return b2; }
        public void setB2(boolean b2) { this.b2 = b2; }
        public boolean isB3() { return b3; }
        public void setB3(boolean b3) { this.b3 = b3; }
    }

    static class SampleRule extends EntityRule<SampleEntity> {
        public SampleRule() {
            super(false);
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> e.isB1(), R1);
            this.addRule(e -> e.isB2(), R2);
            this.addRule(e -> e.isB3(), R3);
        }
    }

    @Test
    public void findRuleByMessageCode_hit_and_miss() {
        SampleRule rule = new SampleRule();
        Assert.assertNotNull(rule.findRuleByMessageCode(R1));
        Assert.assertNull(rule.findRuleByMessageCode(MessageCode.of("NOPE")));
    }

    @Test
    public void findRulesByMessageCode_varargs() {
        SampleRule rule = new SampleRule();
        List<IRule<SampleEntity>> list = rule.findRulesByMessageCode(R1, R3, MessageCode.of("NOPE"));
        Assert.assertEquals(2, list.size());
    }

    @Test
    public void appendRule_positions() {
        SampleRule rule = new SampleRule(); // [R1, R2, R3]
        rule.appendRule(e -> true, MessageCode.of("R_APPEND"), R2, RulePosition.AFTER, null);
        List<io.pragmatic.ddd.rules.RuleItem<SampleEntity>> items = rule.allRuleItems();
        Assert.assertEquals("R1", items.get(0).getMessageCode().code());
        Assert.assertEquals("R2", items.get(1).getMessageCode().code());
        Assert.assertEquals("R_APPEND", items.get(2).getMessageCode().code());
        Assert.assertEquals("R3", items.get(3).getMessageCode().code());
    }

    @Test
    public void removeRule() {
        SampleRule rule = new SampleRule();
        SampleEntity e = new SampleEntity();
        e.setB1(true);
        e.setB3(true);
        rule.removeRule(R2);
        Assert.assertTrue(rule.satisfiesRule(e));
    }

    @Test
    public void replaceRule() {
        SampleRule rule = new SampleRule();
        rule.replaceRule(e -> e.isB1(), R1, MessageCode.of("R1_NEW"));
        Assert.assertNull(rule.findRuleByMessageCode(R1));
        Assert.assertNotNull(rule.findRuleByMessageCode(MessageCode.of("R1_NEW")));
    }

    @Test
    public void satisfiesRule_regression_bugFix() {
        // 普通规则违规时 BrokenRule.getName() 应等于 messageCode.code()，
        // 验证 EntityRule.java:242 原"把 alias 误传 property"的隐藏 bug 已修复。
        SampleRule rule = new SampleRule();
        SampleEntity e = new SampleEntity(); // b1/b2/b3 全 false → 三条规则均失败
        Assert.assertFalse(rule.satisfiesRule(e));
        Assert.assertEquals(3, e.getBrokenRules().size());
        for (BrokenRule br : e.getBrokenRules()) {
            Assert.assertTrue(br.getName().equals("R1")
                    || br.getName().equals("R2")
                    || br.getName().equals("R3"));
        }
    }
}
