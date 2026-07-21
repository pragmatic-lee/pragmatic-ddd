package io.pragmatic.ddd.mybatis.typehandler.enums;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 枚举映射配置注解：就近声明该枚举的持久化策略。
 * 对应设计文档 Step 4（提案 §5.2）。{@code codeField} 当前预留，默认读取 {@code getValue()}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnumMapping {
    /** 持久化策略，缺省 {@link EnumRule#CODE}。 */
    EnumRule strategy() default EnumRule.CODE;

    /** 预留：业务 code 所在字段名；缺省空串表示读取 {@code getValue()}。 */
    String codeField() default "";
}
