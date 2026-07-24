package io.pragmatic.ddd.visual.event;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DomainEventVisual {
    String description() default "";

}
