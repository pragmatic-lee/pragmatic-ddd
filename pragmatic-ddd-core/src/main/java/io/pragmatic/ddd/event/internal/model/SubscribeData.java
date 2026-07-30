package io.pragmatic.ddd.event.internal.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MQ 消息投递数据载体，封装订阅者名、序列化事件体与投递元信息。
 *
 * @author wizard-lee
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeData {

    private String name;
    private String eventData;
    private String realEventName;
    private Boolean onlyThis;
    private DeliveryPolicy deliveryPolicy;
}
