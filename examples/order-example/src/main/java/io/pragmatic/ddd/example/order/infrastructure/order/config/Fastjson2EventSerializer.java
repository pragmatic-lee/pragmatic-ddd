package io.pragmatic.ddd.example.order.infrastructure.order.config;

import com.alibaba.fastjson2.JSON;
import io.pragmatic.ddd.event.spi.IEventSerializer;

/**
 * 基于 fastjson2 的事件序列化实现（示例用最小实现）。
 * 对应设计文档 5.2 / 7.7 节：IEventSerializer 实现优先复用 fastjson2。
 *
 * @author wizard-lee
 */
public class Fastjson2EventSerializer implements IEventSerializer {

    @Override
    public <T> String serialize(T event) {
        return JSON.toJSONString(event);
    }

    @Override
    public <T> T deserialize(String data, Class<T> eventType) {
        return JSON.parseObject(data, eventType);
    }
}
