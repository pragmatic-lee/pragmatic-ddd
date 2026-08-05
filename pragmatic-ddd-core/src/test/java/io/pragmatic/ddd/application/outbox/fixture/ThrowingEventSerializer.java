package io.pragmatic.ddd.application.outbox.fixture;

import io.pragmatic.ddd.event.spi.IEventSerializer;

/**
 * 反序列化即抛异常的事件序列化测试夹具：用于模拟 Relay 补偿路径中"载荷损坏/反序列化失败"场景。
 *
 * @author wizard-lee
 */
public class ThrowingEventSerializer implements IEventSerializer {

    @Override
    public <T> String serialize(T event) {
        return "stub-payload";
    }

    @Override
    public <T> T deserialize(String data, Class<T> eventType) {
        throw new IllegalStateException("deserialize failed");
    }
}
