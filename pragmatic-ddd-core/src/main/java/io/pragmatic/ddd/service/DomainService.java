package io.pragmatic.ddd.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 领域服务声明注解，承载可反射读取的业务元信息。
 * <p>对称于 {@code io.pragmatic.ddd.dependency.ExternalDependency}。
 *
 * @author wizard-lee
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DomainService {

    /**
     * 服务分类，与 {@link IDomainService#category()} 保持一致。必填。
     *
     * @return 领域服务分类
     */
    DomainServiceCategory category();

    /**
     * 业务描述：这个领域服务是干什么的。
     * <p>例如："生成订单唯一标识，由应用层提供具体算法（雪花/序列）"。
     *
     * @return 业务描述
     */
    String description() default "";

    /**
     * 关联对象名（按需填写，按 category 语义解释）：
     * <ul>
     *   <li>事件订阅 —— 处理的事件名（如 "OrderPayedEvent"）</li>
     *   <li>校验规则 —— 作用的领域对象（如 "Order"）</li>
     *   <li>属性计算 —— 作用的领域对象（如 "Order/OrderItem"）</li>
     *   <li>能力供给 —— 产出的领域原语/对象（如 "OrderId"）</li>
     * </ul>
     *
     * @return 关联对象名
     */
    String targetName() default "";
}
