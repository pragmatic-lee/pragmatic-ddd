package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.base.id.IdGeneratorRegistry;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderIdGenerator;
import org.springframework.stereotype.Service;

/**
 * 订单号生成器：领域服务 IOrderIdGenerator 的实现，包装框架 IdGeneratorRegistry，
 * 固定使用 "order" 渠道产出 Long 型订单标识。
 */
@Service
public class OrderIdGenerator implements IOrderIdGenerator {

    private static final String ORDER_BIZ_KEY = "order";

    private final IdGeneratorRegistry idGeneratorRegistry;

    public OrderIdGenerator(IdGeneratorRegistry idGeneratorRegistry) {
        this.idGeneratorRegistry = idGeneratorRegistry;
    }

    @Override
    public Long nextId() {
        return idGeneratorRegistry.nextId(ORDER_BIZ_KEY);
    }
}
