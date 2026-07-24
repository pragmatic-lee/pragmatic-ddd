package io.pragmatic.ddd.visual.entity;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EntityActionVisual {
    Class<?>[] triggerEvents() default {};

    String description() default "";

    String alias() default "";
}
