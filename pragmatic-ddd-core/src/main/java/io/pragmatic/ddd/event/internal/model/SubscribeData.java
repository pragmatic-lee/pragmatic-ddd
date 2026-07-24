package io.pragmatic.ddd.event.internal.model;

/**
 * @author lixiaojing
 */
public class SubscribeData {

    private String name;
    private String eventData;
    private String realEventName;
    private Boolean onlyThis;
    private DeliveryPolicy deliveryPolicy;

    public SubscribeData(String name, String eventData, String realEventName, Boolean onlyThis, DeliveryPolicy deliveryPolicy) {
        this.name = name;
        this.eventData = eventData;
        this.realEventName = realEventName;
        this.onlyThis = onlyThis;
        this.deliveryPolicy = deliveryPolicy;
    }

    public SubscribeData() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }

    public String getRealEventName() {
        return realEventName;
    }

    public void setRealEventName(String realEventName) {
        this.realEventName = realEventName;
    }

    public Boolean getOnlyThis() {
        return onlyThis;
    }

    public void setOnlyThis(Boolean onlyThis) {
        this.onlyThis = onlyThis;
    }

    public DeliveryPolicy getDeliveryPolicy() {
        return deliveryPolicy;
    }

    public void setDeliveryPolicy(DeliveryPolicy deliveryPolicy) {
        this.deliveryPolicy = deliveryPolicy;
    }
}
