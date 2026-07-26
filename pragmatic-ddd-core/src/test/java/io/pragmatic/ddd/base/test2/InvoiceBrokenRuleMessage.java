package io.pragmatic.ddd.base.test2;

import io.pragmatic.ddd.base.BrokenRuleMessage;
import io.pragmatic.ddd.base.MessageCode;

public class InvoiceBrokenRuleMessage extends BrokenRuleMessage {

    public static final MessageCode TITLE_IS_EMPTY_ERROR = MessageCode.of(
            "title_is_empty_error", "title为空");
    public static final MessageCode NO_IS_EMPTY_ERROR = MessageCode.of(
            "no_is_empty_error", "编码为空");

    private InvoiceBrokenRuleMessage() {
    }

    public static final InvoiceBrokenRuleMessage INSTANCE = new InvoiceBrokenRuleMessage();
}
