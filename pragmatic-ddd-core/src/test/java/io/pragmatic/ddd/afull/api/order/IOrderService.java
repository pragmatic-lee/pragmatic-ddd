package io.pragmatic.ddd.afull.api.order;

/**
 * 对外接口，可以dubbo形式的，也可以是rest api形式的
 *
 * @author lixiaojing
 */
public interface IOrderService {

    long createOrder(OrderDto dto);
}
