package io.pragmatic.ddd.visual.service;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 标记领域服务类，承载其可视化展示所需的描述。
 *
 * @author wizard-lee
 */
public @interface DomainServiceVisual {
    /** 领域服务描述。 */
    String description() default "";
}
