package io.pragmatic.ddd.acl;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个接口为本聚合的外部依赖声明。
 * 被标记的接口定义本聚合需要从外部实体（其他聚合或外部系统）获取的数据或能力。
 *
 * @author wizard-lee
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExternalDependency {

    /** 依赖的目标实体名称（聚合名或系统名）。 */
    String targetName();

    /** 依赖类型。 */
    DependencyType type() default DependencyType.AGGREGATE;

    /** 依赖的业务描述。 */
    String description() default "";
}
