package io.pragmatic.ddd.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventSerializer;

/**
 * Fastjson2 序列化实现。
 */
public class Fastjson2EventSerializer implements IEventSerializer {

    @Override
    public <T> String serialize(T event) {
        return JSON.toJSONString(event, JSONWriter.Feature.FieldBased);
    }

    @Override
    public <T> T deserialize(String data, Class<T> eventType) {
        return JSON.parseObject(data, eventType, JSONReader.Feature.FieldBased);
    }
}
