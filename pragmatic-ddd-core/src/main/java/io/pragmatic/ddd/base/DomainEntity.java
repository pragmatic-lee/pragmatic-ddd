package io.pragmatic.ddd.base;

import java.lang.annotation.*;

/**
 * 标记一个类为 Pragmatic DDD 框架中的领域实体。
 * 提供聚合根、业务描述与限界上下文等语义元数据，供可视化与元数据导出使用。
 *
 * @author wizard-lee
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DomainEntity {

    /** 返回该实体所属聚合根名称；聚合根本身为自身简单类名。 */
    String aggregateRoot() default "";

    /** 返回实体的业务用途描述。 */
    String description() default "";

    /** 返回实体所属限界上下文。 */
    String boundedContext() default "";
}
