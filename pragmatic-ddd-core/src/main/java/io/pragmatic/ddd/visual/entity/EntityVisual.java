package io.pragmatic.ddd.visual.entity;


import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 标记实体类或字段，承载其可视化展示所需的描述。
 *
 * @author wizard-lee
 */
public @interface EntityVisual {

    /** 实体/字段描述。 */
    String description() default "";

}
