package io.pragmatic.ddd.visual.rule;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EntityRuleVisual {
    String description() default "";
}
