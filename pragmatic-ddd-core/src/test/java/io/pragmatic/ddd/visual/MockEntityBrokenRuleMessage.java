package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.BrokenRuleMessage;

public class MockEntityBrokenRuleMessage extends BrokenRuleMessage {
    public static final String Name_Error = "Name_Error";

    public static final BrokenRuleMessage message = new MockEntityBrokenRuleMessage();

    @Override
    protected void populateMessage() {
        this.getMessages().put(Name_Error, "名字错误");
    }
}
