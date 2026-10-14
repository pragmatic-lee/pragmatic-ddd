package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.base.id.IdGeneratorDefinition;
import io.pragmatic.ddd.base.id.IdType;
import io.pragmatic.ddd.example.order.application.order.service.OrderIdGenerator;
import io.pragmatic.ddd.base.id.IdGeneratorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ID 生成器业务侧装配：仅登记订单渠道的号段定义，注册中心与号段分配器由
 * {@code pragmatic-ddd-spring-boot} 的 {@code DddIdGeneratorAutoConfiguration} 自动装配
 * （其依据容器内全部 {@link IdGeneratorDefinition} Bean 逐个注册渠道生成器）。
 * 数据库与 id_segment 表假定已存在（含 order 渠道初始行），本配置不负责建表与初始化数据。
 *
 * @author wizard-lee
 */
@Configuration
public class IdGeneratorConfig {

    private static final String ORDER_BIZ_KEY = "order";

    /**
     * 订单渠道的号段定义：交 starter 的注册中心据此建立订单 ID 生成器。
     *
     * @return 订单号段定义
     */
    @Bean
    public IdGeneratorDefinition orderIdGeneratorDefinition() {
        return new IdGeneratorDefinition(ORDER_BIZ_KEY, 1L, 1000, IdType.LONG, null, "订单号");
    }

    /**
     * 订单 ID 生成器：依赖 starter 产出的 {@link IdGeneratorRegistry} 按渠道取号。
     *
     * @param idGeneratorRegistry starter 装配的号段注册中心
     * @return 订单 ID 生成器
     */
    @Bean
    public OrderIdGenerator orderIdGenerator(IdGeneratorRegistry idGeneratorRegistry) {
        return new OrderIdGenerator(idGeneratorRegistry);
    }
}
