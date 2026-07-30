package io.pragmatic.ddd.visual.rule;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 标记领域规则类，承载其可视化展示所需的描述。
 *
 * @author wizard-lee
 */
public @interface EntityRuleVisual {
    /** 领域规则描述。 */
    String description() default "";
}
