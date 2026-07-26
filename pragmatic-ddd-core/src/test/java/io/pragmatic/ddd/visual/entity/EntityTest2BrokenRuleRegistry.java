package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

public class EntityTest2BrokenRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode testError = MessageCode.of("testError", "testError");

    public static final EntityTest2BrokenRuleRegistry INSTANCE = new EntityTest2BrokenRuleRegistry();
}
