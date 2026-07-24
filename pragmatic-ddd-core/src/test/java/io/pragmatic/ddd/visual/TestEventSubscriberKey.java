package io.pragmatic.ddd.visual;


import io.pragmatic.ddd.base.AbstractSubscriberKey;

public class TestEventSubscriberKey extends AbstractSubscriberKey {
    public static final String SUB1 = "sub1";
    public static final String SUB2 = "sub2";
    public static final String SUB3 = "sub3";

    public static final TestEventSubscriberKey subscriberKey = new TestEventSubscriberKey();

    @Override
    protected void populateKeys() {
        this.getKeys().put(SUB1, buildKeySetting("sub1描述"));
        this.getKeys().put(SUB2, buildKeySetting("sub2描述"));
        this.getKeys().put(SUB3, buildKeySetting("sub3描述"));
    }
}
