package io.pragmatic.ddd.subscriber;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import org.junit.Test;

public class AbstractSubscriberKeyTest {

    @Test
    public void subscriberKeyTest(){
        TestSubscriberKey testSubscriberKey = new TestSubscriberKey();

       assert  testSubscriberKey.getKeys().size() == 3;
    }

}

class TestSubscriberKey extends AbstractSubscriberKey {


    public static final String WRITE_ES = "write_es";
    public static final String WRITE_JIMDB = "write_jimdb";
    public static final String SEND_MESSAGE = "send_messsage";

    @Override
    protected void populateKeys() {
        this.getKeys().put(WRITE_ES, buildKeySetting("写ES"));
        this.getKeys().put(WRITE_JIMDB, buildKeySetting("写jimdb"));
        this.getKeys().put(SEND_MESSAGE, buildKeySetting("发送消息", true));
    }
}
