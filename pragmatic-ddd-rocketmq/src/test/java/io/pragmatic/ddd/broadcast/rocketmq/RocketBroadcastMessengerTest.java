package io.pragmatic.ddd.broadcast.rocketmq;

import io.pragmatic.ddd.broadcast.BroadcastSendException;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocketBroadcastMessengerTest {

    /** 内存 MQProducer 代理：仅 send 有真实行为，其余接口方法返回 null，避免触达真实 broker。 */
    private record StubProducer(Message captured, RuntimeException failWith) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("send".equals(method.getName()) && args != null && args.length == 1) {
                if (this.failWith != null) {
                    throw this.failWith;
                }
                return new SendResult();
            }
            return null;
        }

        static MQProducer create(RuntimeException failWith) {
            return (MQProducer) Proxy.newProxyInstance(
                    MQProducer.class.getClassLoader(),
                    new Class<?>[] { MQProducer.class },
                    new StubProducer(null, failWith));
        }
    }

    @Test
    void sendShouldDelegateToProducerWithCorrectTopicTagsKeysAndBody() throws Exception {
        MQProducer producer = StubProducer.create(null);
        RocketBroadcastMessenger messenger = new RocketBroadcastMessenger(producer);

        messenger.send("topic-order-external", "broadcast", "serialized-json");

        // 通过再次代理捕获 send 入参：用 capture 变体
        CapturingHandler handler = new CapturingHandler();
        MQProducer capturing = (MQProducer) Proxy.newProxyInstance(
                MQProducer.class.getClassLoader(), new Class<?>[] { MQProducer.class }, handler);
        new RocketBroadcastMessenger(capturing).send("topic-order-external", "broadcast", "serialized-json");

        Message sent = handler.captured;
        assertThat(sent.getTopic()).isEqualTo("topic-order-external");
        assertThat(sent.getTags()).isNull();
        assertThat(sent.getKeys()).isEqualTo("broadcast");
        assertThat(new String(sent.getBody())).isEqualTo("serialized-json");
    }

    @Test
    void sendFailureShouldWrapAsBroadcastSendException() {
        MQProducer producer = StubProducer.create(new RuntimeException("broker unavailable"));
        RocketBroadcastMessenger messenger = new RocketBroadcastMessenger(producer);

        assertThatThrownBy(() -> messenger.send("topic-x", "broadcast", "body"))
                .isInstanceOf(BroadcastSendException.class)
                .hasMessageContaining("topic-x")
                .hasRootCauseMessage("broker unavailable");
    }

    @Test
    void shouldRejectNullProducer() {
        assertThatThrownBy(() -> new RocketBroadcastMessenger(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static final class CapturingHandler implements InvocationHandler {
        Message captured;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("send".equals(method.getName()) && args != null && args.length == 1) {
                this.captured = (Message) args[0];
                return new SendResult();
            }
            return null;
        }
    }
}
