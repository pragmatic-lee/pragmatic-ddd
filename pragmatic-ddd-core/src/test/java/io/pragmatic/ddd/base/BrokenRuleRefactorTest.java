package io.pragmatic.ddd.base;

import io.pragmatic.ddd.operation.OperationRegistry;
import org.junit.Assert;
import org.junit.Test;

/**
 * 验证本次重构后的核心行为：
 * 1. BrokenRuleMessage 构造时反射自动注册 static MessageCode 字段；
 * 2. BrokenRuleObject 仅接受 MessageCode 的 addBrokenRule / addParamBrokenRule；
 * 3. BrokenRule 不再承载 property / alias。
 */
public class BrokenRuleRefactorTest {

    static class SampleMessage extends BrokenRuleMessage {
        public static final MessageCode NAME_ERROR = MessageCode.of("NAME_ERROR", "名称不能为空");
        public static final MessageCode AGE_ERROR = MessageCode.of("AGE_ERROR", "年龄不合法:%s");
        public static final SampleMessage INSTANCE = new SampleMessage();
    }

    static class SampleEntity extends AbstractEntity<Long> {
        SampleEntity() {
            this.setEntityId(1L);
        }

        @Override
        protected BrokenRuleMessage getBrokenRuleMessages() {
            return SampleMessage.INSTANCE;
        }

        @Override
        protected OperationRegistry entityOperations() {
            return null;
        }
    }

    @Test
    public void reflectionRegistersStaticMessageCodes() {
        Assert.assertEquals("名称不能为空", SampleMessage.INSTANCE.getRuleDescription("NAME_ERROR"));
        Assert.assertEquals("", SampleMessage.INSTANCE.getRuleDescription("UNKNOWN"));
    }

    @Test
    public void addBrokenRuleStoresMessageCode() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleMessage.NAME_ERROR);

        Assert.assertEquals(1, entity.getBrokenRules().size());
        BrokenRule rule = entity.getBrokenRules().get(0);
        Assert.assertEquals("NAME_ERROR", rule.getName());
        Assert.assertEquals("名称不能为空", rule.getDescription());
        Assert.assertNull(rule.getExtraData());
    }

    @Test
    public void addParamBrokenRuleFormatsMessage() {
        SampleEntity entity = new SampleEntity();
        entity.addParamBrokenRule(SampleMessage.AGE_ERROR, new Object[]{18}, true);

        Assert.assertEquals("年龄不合法:18", entity.getBrokenRules().get(0).getDescription());
    }

    @Test
    public void addParamBrokenRuleNoFormat() {
        SampleEntity entity = new SampleEntity();
        entity.addParamBrokenRule(SampleMessage.AGE_ERROR, new Object[]{18}, false);

        Assert.assertEquals("年龄不合法:%s", entity.getBrokenRules().get(0).getDescription());
    }

    @Test
    public void ofInlineFactoryRegistersCodes() {
        BrokenRuleMessage inline = BrokenRuleMessage.of(MessageCode.of("INLINE", "内联"));
        Assert.assertEquals("内联", inline.getRuleDescription("INLINE"));
    }
}
