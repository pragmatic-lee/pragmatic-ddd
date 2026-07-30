package io.pragmatic.ddd.visual.event;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 标记领域事件类，承载其可视化展示所需的描述。
 *
 * @author wizard-lee
 */
public @interface DomainEventVisual {
    /** 领域事件描述。 */
    String description() default "";

}
