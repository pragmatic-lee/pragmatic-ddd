package io.pragmatic.ddd.base;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应设计文档阶段 6.1：MessageCode（record 值对象）单元测试。
 */
public class MessageCodeTest {

    @Test
    public void of_withDescription() {
        MessageCode code = MessageCode.of("NAME_ERROR", "名称不能为空");
        Assert.assertEquals("NAME_ERROR", code.localCode());
        Assert.assertEquals("名称不能为空", code.description());
        Assert.assertEquals("NAME_ERROR", code.code());
    }

    @Test
    public void of_singleArg_descriptionEmpty() {
        MessageCode code = MessageCode.of("AGE_ERROR");
        Assert.assertEquals("AGE_ERROR", code.localCode());
        Assert.assertEquals("", code.description());
    }

    @Test
    public void equals_byLocalCode() {
        MessageCode a = MessageCode.of("X", "desc1");
        MessageCode b = MessageCode.of("X", "desc2");
        MessageCode c = MessageCode.of("Y", "desc1");

        Assert.assertEquals(a, b);
        Assert.assertNotEquals(a, c);
        Assert.assertNotEquals(a, null);
        Assert.assertNotEquals(a, "X");
        Assert.assertEquals(a, a);
    }

    @Test
    public void hashCode_consistent() {
        MessageCode a = MessageCode.of("X", "desc1");
        MessageCode b = MessageCode.of("X", "desc2");
        Assert.assertEquals(a.hashCode(), b.hashCode());

        Map<MessageCode, String> map = new HashMap<>();
        map.put(a, "v");
        Assert.assertEquals("v", map.get(b));
    }

    @Test
    public void component_accessors_consistent() {
        MessageCode code = MessageCode.of("X", "d");
        Assert.assertEquals(code.localCode(), code.code());
    }
}
