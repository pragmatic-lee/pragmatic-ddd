package io.pragmatic.ddd.base.test2.rule;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

public class PersonBrokenRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode NAME_ERROR = MessageCode.of("NAME_ERROR", "名称不能为空");
    public static final MessageCode AGE_ERROR = MessageCode.of("AGE_ERROR", "");
    public static final MessageCode PHONE_ERROR = MessageCode.of("PHONE_ERROR", "电话不能为空");
    public static final MessageCode EMAIL_ERROR = MessageCode.of("EMAIL_ERROR", "电子邮件不能为空");
    public static final MessageCode STATUS_ERROR = MessageCode.of("STATUS_ERROR", "状态错误");
    public static final MessageCode PERSON_SCORE_ERROR = MessageCode.of("PERSON_SCORE_ERROR", "score 错误");

    public static final PersonBrokenRuleRegistry INSTANCE = new PersonBrokenRuleRegistry();
}
