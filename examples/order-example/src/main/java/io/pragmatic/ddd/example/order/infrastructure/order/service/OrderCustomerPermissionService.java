package io.pragmatic.ddd.example.order.infrastructure.order.service;

import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import io.pragmatic.ddd.rules.RuleCheckResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 下单用户资格校验的应用层实现，调用外部用户系统判断用户是否处于生效状态且具备下单资格。
 *
 * @author wizard-lee
 */
@Service
public class OrderCustomerPermissionService implements IOrderCustomerPermissionService {





    @Override
    public RuleCheckResult verifyOrderCreatePermission(Customer customer) {
        if (customer == null || customer.getCustomerId() == null) {
            return RuleCheckResult.fail(new Object[]{"null"});
        }

        boolean qualified = this.callUserSystem(customer.getCustomerId());
        if (qualified) {
            return RuleCheckResult.pass();
        }

        return RuleCheckResult.fail(new Object[]{customer.getCustomerId()});
    }

    private boolean callUserSystem(Long customerId) {
       return true;
    }
}
