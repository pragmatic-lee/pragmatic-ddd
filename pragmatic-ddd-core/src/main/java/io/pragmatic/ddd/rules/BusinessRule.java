package io.pragmatic.ddd.rules;

import java.lang.annotation.*;

/**
 * 标记领域实体或规则类中的方法为一条业务规则。
 *
 * <p>该注解承载业务规则的元信息，可被 AI 编码辅助与模型可视化系统消费，
 * 用于记录每条规则的意图、错误码与消息。</p>
 *
 * @author wizard-lee
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessRule {

    /** 规则校验内容的可读描述。 */
    String description() default "";

    /**
     * 规则被违反时使用的错误码。
     * <p>对应实体 {@code BrokenRuleRegistry} 中的键。</p>
     */
    String errorCode() default "";

    /** 规则被违反时展示的可读错误消息。 */
    String errorMessage() default "";
}
