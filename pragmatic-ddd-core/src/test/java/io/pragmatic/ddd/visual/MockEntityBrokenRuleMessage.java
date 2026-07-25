package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.BrokenRuleMessage;
import io.pragmatic.ddd.base.MessageCode;

public class MockEntityBrokenRuleMessage extends BrokenRuleMessage {
    public static final MessageCode Name_Error = MessageCode.of("Name_Error", "名字错误");

    public static final MockEntityBrokenRuleMessage INSTANCE = new MockEntityBrokenRuleMessage();
}
