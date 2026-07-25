package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.BrokenRuleMessage;
import io.pragmatic.ddd.base.MessageCode;

public class EntityTest2BrokenRuleMessage extends BrokenRuleMessage {

    public static final MessageCode testError = MessageCode.of("testError", "testError");

    public static final EntityTest2BrokenRuleMessage INSTANCE = new EntityTest2BrokenRuleMessage();
}
