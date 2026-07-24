package io.pragmatic.ddd.base.test2.rule;

import io.pragmatic.ddd.base.BrokenRuleMessage;

public class PersonBrokeRuleMessage extends BrokenRuleMessage {

    public static final String NAME_ERROR = "NAME_ERROR";
    public static final String AGE_ERROR = "AGE_ERROR";
    public static final String PHONE_ERROR = "PHONE_ERROR";
    public static final String EMAIL_ERROR = "EMAIL_ERROR";
    public static final String STATUS_ERROR = "STATUS_ERROR";
    public static final String PERSON_SCORE_ERROR = "PERSON_SCORE_ERROR";

    public static final BrokenRuleMessage INSTANCE = new PersonBrokeRuleMessage();

    @Override
    protected void populateMessage() {

        this.getMessages().put(NAME_ERROR, "名称不能为空");
        this.getMessages().put(PHONE_ERROR, "电话不能为空");
        this.getMessages().put(EMAIL_ERROR, "电子邮件不能为空");
        this.getMessages().put(STATUS_ERROR, "状态错误");
        this.getMessages().put(PERSON_SCORE_ERROR,"score 错误");

    }
}


