package io.pragmatic.ddd.visual.application;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CommandServiceVisual {

    String name() default "";
    String description() default "";
}
