package io.pragmatic.ddd.example.order.infrastructure.order.service;

import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import io.pragmatic.ddd.rules.RuleCheckResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 下单用户资格校验的应用层实现，调用外部用户系统判断用户是否处于生效状态且具备下单资格。
 *
 * @author wizard-lee
 */
public class OrderCustomerPermissionService implements IOrderCustomerPermissionService {

    private final RestTemplate restTemplate;

    private final String userServiceUrl;

    public OrderCustomerPermissionService(
            RestTemplate restTemplate,
            @Value("${user.service.url:}") String userServiceUrl) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
    }

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
        if (this.userServiceUrl == null || this.userServiceUrl.isBlank()) {
            throw new IllegalStateException(
                    "user.service.url is not configured, cannot verify customer order permission");
        }

        String url = this.userServiceUrl + "/users/" + customerId + "/order-permission";
        ResponseEntity<PermissionResponse> response =
                this.restTemplate.getForEntity(url, PermissionResponse.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return Boolean.TRUE.equals(response.getBody().qualified());
        }

        return false;
    }

    /**
     * 外部用户系统返回的资格判定响应。
     */
    public static class PermissionResponse {

        private Boolean qualified;

        public Boolean qualified() {
            return this.qualified;
        }

        public void setQualified(Boolean qualified) {
            this.qualified = qualified;
        }
    }
}
