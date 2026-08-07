package io.pragmatic.ddd.afull.api.order;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建订单请求的数据传输对象。
 *
 * @author wizard-lee
 */
public class OrderDto {
    public String pin;
    public String comment;
    public List<OrderItemDto> orderItemDtoList = new ArrayList<>();
}
