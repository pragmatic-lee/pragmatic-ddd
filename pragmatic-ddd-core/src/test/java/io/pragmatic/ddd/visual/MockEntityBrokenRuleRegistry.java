package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

public class MockEntityBrokenRuleRegistry extends BrokenRuleRegistry {
    public static final MessageCode Name_Error = MessageCode.of("Name_Error", "名字错误");

    public static final MockEntityBrokenRuleRegistry INSTANCE = new MockEntityBrokenRuleRegistry();
}
