package io.pragmatic.ddd.afull.api.order;

/**
 * 订单对外服务接口，可以是 dubbo 形式或 rest api 形式。
 *
 * @author wizard-lee
 */
public interface IOrderService {

    long createOrder(OrderDto dto);
}
