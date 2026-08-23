package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.base.id.IdGeneratorDefinition;
import io.pragmatic.ddd.base.id.IdGeneratorRegistry;
import io.pragmatic.ddd.base.id.IdType;
import io.pragmatic.ddd.example.order.application.order.service.OrderIdGenerator;
import io.pragmatic.ddd.mybatis.id.DbSegmentAllocator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ID 生成器基础设施配置：装配框架号段机制与订单渠道。
 * 数据库与 id_segment 表假定已存在（含 order 渠道初始行），本配置不负责建表与初始化数据。
 */
@Configuration
public class IdGeneratorConfig {

    private static final String ORDER_BIZ_KEY = "order";

    @Bean
    public IdGeneratorRegistry idGeneratorRegistry(SqlSessionFactory sqlSessionFactory) {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        DbSegmentAllocator allocator = new DbSegmentAllocator(sqlSessionFactory);
        IdGeneratorDefinition definition =
                new IdGeneratorDefinition(ORDER_BIZ_KEY, 1L, 1000, IdType.LONG, null, "订单号");
        registry.register(definition, allocator);
        return registry;
    }

    @Bean
    public OrderIdGenerator orderIdGenerator(IdGeneratorRegistry idGeneratorRegistry) {
        return new OrderIdGenerator(idGeneratorRegistry);
    }
}
