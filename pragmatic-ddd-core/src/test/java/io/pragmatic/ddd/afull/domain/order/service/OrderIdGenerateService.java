package io.pragmatic.ddd.afull.domain.order.service;

import java.util.Random;

/**
 * 订单ID生成服务
 *
 * @author lixiaojing
 * @date 2021/3/1 6:10 下午
 */
public class OrderIdGenerateService {
    public long genOrderId() {
        return new Random().nextLong();
    }
}
