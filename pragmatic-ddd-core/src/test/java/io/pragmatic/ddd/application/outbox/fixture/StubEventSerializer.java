package io.pragmatic.ddd.application.outbox.fixture;

import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.event.spi.IEventSerializer;

/**
 * 事件序列化测试夹具：serialize 返回固定载荷；deserialize 反序列化为一个固定 entityId 的 SampleEvent。
 *
 * @author wizard-lee
 */
public class StubEventSerializer implements IEventSerializer {

    @Override
    public <T> String serialize(T event) {
        return "stub-payload";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(String data, Class<T> eventType) {
        return (T) new SampleEvent("stub-entity");
    }
}
