package io.pragmatic.ddd.service;

/**
 * 领域服务标记接口。
 * <p>领域服务分为四类（与 {@link DomainService} 注解的 category 对应）：
 * 事件订阅 / 校验规则 / 属性计算（类型转换） / 领域工厂·能力供给。
 * 领域层负责定义接口并标注 {@link DomainService}，应用层负责提供实现。
 *
 * @author wizard-lee
 */
public interface IDomainService {

    /**
     * 返回领域服务的分类类型，默认读取 {@link DomainService} 注解；未标注则为 {@link DomainServiceCategory#UNKNOWN}。
     *
     * @return 领域服务分类
     */
    default DomainServiceCategory category() {
        DomainService annotation = findAnnotation(this.getClass());
        return annotation == null ? DomainServiceCategory.UNKNOWN : annotation.category();
    }

    private static DomainService findAnnotation(Class<?> target) {
        if (target == null || target == Object.class) {
            return null;
        }
        DomainService annotation = target.getAnnotation(DomainService.class);
        if (annotation != null) {
            return annotation;
        }
        for (Class<?> interfaceClass : target.getInterfaces()) {
            DomainService found = findAnnotation(interfaceClass);
            if (found != null) {
                return found;
            }
        }
        return findAnnotation(target.getSuperclass());
    }
}
