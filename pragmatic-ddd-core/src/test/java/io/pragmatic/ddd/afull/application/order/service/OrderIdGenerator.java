package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.service.IOrderIdGenerator;

import java.util.Random;

/**
 * 订单 ID 生成实现。
 *
 * @author wizard-lee
 */
public class OrderIdGenerator implements IOrderIdGenerator {

    private final Random random = new Random();

    @Override
    public long generate() {
        return random.nextLong();
    }
}
