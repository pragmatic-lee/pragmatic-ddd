package io.pragmatic.ddd.example.order.domain.order.service;

import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;
import io.pragmatic.ddd.service.ICheckRuleService;

/**
 * 下单用户资格校验契约，校验外部用户是否处于生效状态且具备下单资格。
 * <p>本契约为 BUSINESS_RULE 类校验领域服务，贴合 {@code ICheckRule} 契约（以 Customer 为唯一校验模型、忽略新旧对比），
 * 故继承 {@link ICheckRuleService}；{@code verifyOrderCreatePermission} 为领域语义入口，由默认 {@code check} 桥接至 {@code ICheckRule}。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.BUSINESS_RULE,
        targetName = "Order",
        description = "校验下单用户是否生效且具备下单资格")
public interface IOrderCustomerPermissionService extends ICheckRuleService<Customer> {

    /**
     * 校验指定用户是否处于生效状态且具备下单资格。
     *
     * @param customer 下单客户
     * @return 校验结果
     */
    RuleCheckResult verifyOrderCreatePermission(Customer customer);

    /**
     * 实现 {@code ICheckRule} 抽象契约，忽略新旧模型对比，委托给 {@link #verifyOrderCreatePermission(Customer)}。
     *
     * @param newCustomer 当前下单客户
     * @param oldCustomer 修改前的客户，本规则不关心，传 null
     * @return 校验结果
     */
    @Override
    default RuleCheckResult check(Customer newCustomer, Customer oldCustomer) {
        return verifyOrderCreatePermission(newCustomer);
    }
}
