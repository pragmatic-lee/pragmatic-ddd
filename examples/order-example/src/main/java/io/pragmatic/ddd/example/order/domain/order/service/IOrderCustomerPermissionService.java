package io.pragmatic.ddd.example.order.domain.order.service;

import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;
import io.pragmatic.ddd.service.IDomainService;

/**
 * 下单用户资格校验契约，校验外部用户是否处于生效状态或具备下单资格。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.RULE_VALIDATOR,
        targetName = "Order",
        description = "校验下单用户是否生效且具备下单资格")
public interface IOrderCustomerPermissionService extends IDomainService {

    /**
     * 校验指定用户是否处于生效状态且具备下单资格。
     *
     * @param customer 下单客户
     * @return 校验结果
     */
    RuleCheckResult verifyOrderCreatePermission(Customer customer);
}
