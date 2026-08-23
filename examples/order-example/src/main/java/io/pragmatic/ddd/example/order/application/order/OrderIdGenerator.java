package io.pragmatic.ddd.example.order.application.order;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单标识生成默认实现：示例环境使用原子自增，生产可替换为雪花算法等。
 */
@Component
public class OrderIdGenerator implements IOrderIdGenerator {

    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public Long nextId() {
        return sequence.incrementAndGet();
    }
}
