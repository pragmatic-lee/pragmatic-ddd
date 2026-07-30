package io.pragmatic.ddd.visual.entity;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 标记实体方法或构造器为可可视化行为，承载触发事件、描述与别名。
 *
 * @author wizard-lee
 */
public @interface EntityActionVisual {
    /** 触发领域事件类型列表。 */
    Class<?>[] triggerEvents() default {};

    /** 行为描述。 */
    String description() default "";

    /** 行为展示别名（缺省用方法名/类名）。 */
    String alias() default "";
}
