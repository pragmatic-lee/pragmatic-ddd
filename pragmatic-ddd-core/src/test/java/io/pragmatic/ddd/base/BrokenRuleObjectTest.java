package io.pragmatic.ddd.base;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * 对应设计文档阶段 6.3：BrokenRuleObject（仅 MessageCode API）单元测试。
 */
public class BrokenRuleObjectTest {

    public static class SampleBrokenRuleMessage extends BrokenRuleMessage {
        public static final MessageCode NAME_ERROR = MessageCode.of("NAME_ERROR", "名称:%s 不能为空");
        public static final MessageCode AGE_ERROR = MessageCode.of("AGE_ERROR", "年龄不合法");
        public static final SampleBrokenRuleMessage INSTANCE = new SampleBrokenRuleMessage();
    }

    static class SampleEntity extends BrokenRuleObject {
        @Override
        protected BrokenRuleMessage getBrokenRuleMessages() {
            return SampleBrokenRuleMessage.INSTANCE;
        }
    }

    @Test
    public void addBrokenRule_messageCode() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleBrokenRuleMessage.NAME_ERROR);
        List<BrokenRule> rules = entity.getBrokenRules();
        Assert.assertEquals(1, rules.size());
        Assert.assertEquals("NAME_ERROR", rules.get(0).getName());
        Assert.assertEquals("名称:%s 不能为空", rules.get(0).getDescription());
    }

    @Test
    public void addParamBrokenRule_format_true() {
        SampleEntity entity = new SampleEntity();
        Object[] params = new Object[]{"张三"};
        entity.addParamBrokenRule(SampleBrokenRuleMessage.NAME_ERROR, params, true);
        BrokenRule rule = entity.getBrokenRules().get(0);
        Assert.assertEquals("名称:张三 不能为空", rule.getDescription());
        Assert.assertArrayEquals(params, rule.getExtraData());
    }

    @Test
    public void addParamBrokenRule_format_false() {
        SampleEntity entity = new SampleEntity();
        Object[] params = new Object[]{"张三"};
        entity.addParamBrokenRule(SampleBrokenRuleMessage.NAME_ERROR, params, false);
        BrokenRule rule = entity.getBrokenRules().get(0);
        Assert.assertEquals("名称:%s 不能为空", rule.getDescription());
    }

    @Test
    public void exceptionCause_passes_extraData() {
        SampleEntity entity = new SampleEntity();
        Object[] params = new Object[]{"张三"};
        entity.addParamBrokenRule(SampleBrokenRuleMessage.NAME_ERROR, params, true);
        BrokenRuleException ex = entity.exceptionCause();
        Assert.assertNotNull(ex);
        Assert.assertEquals("NAME_ERROR", ex.getCode());
        Assert.assertEquals("名称:张三 不能为空", ex.getMessage());
        Assert.assertArrayEquals(params, ex.getExtraData());
    }

    @Test
    public void aggregateExceptionCause_size_matches() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleBrokenRuleMessage.NAME_ERROR);
        entity.addBrokenRule(SampleBrokenRuleMessage.AGE_ERROR);
        BrokenRuleAggregateException ex = entity.aggregateExceptionCause();
        Assert.assertNotNull(ex);
        Assert.assertEquals(2, ex.getExceptions().size());
    }

    @Test
    public void clearBrokenRules_exceptionCause_null() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleBrokenRuleMessage.NAME_ERROR);
        entity.clearBrokenRules();
        Assert.assertNull(entity.exceptionCause());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getBrokenRules_immutable() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleBrokenRuleMessage.NAME_ERROR);
        entity.getBrokenRules().add(new BrokenRule("X", "x"));
    }
}
