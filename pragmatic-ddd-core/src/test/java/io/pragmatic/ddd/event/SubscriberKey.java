package io.pragmatic.ddd.event;


import io.pragmatic.ddd.base.AbstractSubscriberKey;

public class SubscriberKey extends AbstractSubscriberKey {

    public static final String SUB1 = "sub1";
    public static final String SUB2 = "sub2";
    public static final String SUB3 = "sub3";


    public static final SubscriberKey subscriberKey = new SubscriberKey();

    @Override
    protected void populateKeys() {

        this.getKeys().put(SUB1, buildKeySetting("订阅Sub1"));
        this.getKeys().put(SUB2, buildKeySetting("订阅Sub2"));
        this.getKeys().put(SUB3, buildKeySetting("订阅Sub3"));

    }
}
