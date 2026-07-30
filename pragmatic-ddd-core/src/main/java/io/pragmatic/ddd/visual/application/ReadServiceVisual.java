package io.pragmatic.ddd.visual.application;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 标记查询型应用服务方法，承载其可视化展示所需的名称与描述。
 *
 * @author wizard-lee
 */
public @interface ReadServiceVisual {

    /** 服务展示名称。 */
    String name() default "";

    /** 服务描述。 */
    String description() default "";
}
