package io.pragmatic.ddd.base;

import java.util.HashMap;

public abstract class BrokenRuleMessage {

    private final HashMap<String, String> messages;

    protected BrokenRuleMessage() {
        this.messages = new HashMap<>();
        this.populateMessage();
    }

    protected HashMap<String, String> getMessages() {
        return this.messages;
    }

    protected abstract void populateMessage();

    public String getRuleDescription(String messageKey) {

        String desc = "";
        if (this.messages.containsKey(messageKey)) {
            desc = this.messages.get(messageKey);
        }
        return desc;
    }

    public BrokenRuleException createException(String messageKey) {
        return new BrokenRuleException(messageKey, this.getRuleDescription(messageKey));
    }

    public BrokenRuleException createExceptionWithParam(String messageKey, Object[] params) {

        String ruleDescription = this.getRuleDescription(messageKey);
        return new BrokenRuleException(messageKey, String.format(ruleDescription, params));

    }
}
