package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.BrokenRuleMessage;

public class EntityTest2BrokenRuleMessage extends BrokenRuleMessage {

    public static final String testError = "testError";

    @Override
    protected void populateMessage() {

        this.getMessages().put(testError, "testError");

    }
}
