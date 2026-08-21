package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import io.pragmatic.ddd.example.order.infrastructure.order.service.OrderCustomerPermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 订单领域服务构件登记配置，集中暴露外部用户资格校验服务实现。
 *
 * @author wizard-lee
 */
@Configuration
public class OrderServiceConfig {

    @Value("${user.service.url:}")
    private String userServiceUrl;

    /**
     * 构建调用外部用户系统的 REST 客户端。
     *
     * @return REST 客户端
     */
    @Bean
    public RestTemplate orderRestTemplate() {
        return new RestTemplate();
    }

    /**
     * 构建下单用户资格校验服务，依赖外部用户系统接口。
     *
     * @param restTemplate REST 客户端
     * @return 下单用户资格校验服务
     */
    @Bean
    public IOrderCustomerPermissionService orderCustomerPermissionService(RestTemplate restTemplate) {
        return new OrderCustomerPermissionService(restTemplate, this.userServiceUrl);
    }
}
