package io.pragmatic.ddd.visual.service;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DomainServiceVisual {
    String description() default "";
}
