package io.pragmatic.ddd.event;

import java.lang.annotation.*;

/**
 * 标记一个业务方法为某领域事件的触发点。
 * 记录方法与事件的对应关系，便于理解事件流与自动化文档/架构分析。
 *
 * @author wizard-lee
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventTrigger {

    /** 触发该方法的领域事件类。 */
    Class<?> eventClass() default Void.class;

    /** 触发时机与原因的可读描述。 */
    String description() default "";

    /** 触发该事件的方法名，用于文档与追踪。 */
    String afterMethod() default "";
}
