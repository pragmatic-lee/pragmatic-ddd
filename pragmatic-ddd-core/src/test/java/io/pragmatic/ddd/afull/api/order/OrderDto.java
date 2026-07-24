package io.pragmatic.ddd.afull.api.order;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lixiaojing
 */
public class OrderDto {
    public String pin;
    public String comment;
    public List<OrderItemDto> orderItemDtoList = new ArrayList<>();
}
