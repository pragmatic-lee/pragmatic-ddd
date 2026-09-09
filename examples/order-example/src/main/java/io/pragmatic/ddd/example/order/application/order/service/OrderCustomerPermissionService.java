package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.dependency.IUserDependency;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import io.pragmatic.ddd.rules.RuleCheckResult;
import org.springframework.stereotype.Service;

/**
 * 下单用户资格校验的应用层实现，委派至用户依赖端口 IUserDependency 判定用户是否生效且具备下单资格。
 *
 * @author wizard-lee
 */
@Service
public class OrderCustomerPermissionService implements IOrderCustomerPermissionService {

    private final IUserDependency userDependency;

    public OrderCustomerPermissionService(IUserDependency userDependency) {
        this.userDependency = userDependency;
    }

    @Override
    public RuleCheckResult verifyOrderCreatePermission(Customer customer) {
        if (customer == null || customer.getCustomerId() == null) {
            return RuleCheckResult.fail(new Object[]{"null"});
        }

        boolean qualified = userDependency.isUserQualified(customer.getCustomerId().toString());
        if (qualified) {
            return RuleCheckResult.pass();
        }

        return RuleCheckResult.fail(new Object[]{customer.getCustomerId()});
    }
}
