package io.pragmatic.ddd.kafka;

import com.alibaba.fastjson2.JSONReader;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import com.alibaba.fastjson2.JSON;

/**
 * Kafka 事件序列化器，基于 fastjson2 在对象与字符串之间转换。
 * 因 RocketMQ 的同名实现处于其自有包内、不可跨模块依赖，本模块自带等价实现以保持解耦。
 *
 * @author wizard-lee
 */
public class KafkaEventSerializer implements IEventSerializer {

    @Override
    public <T> String serialize(T event) {
        return JSON.toJSONString(event);
    }

    @Override
    public <T> T deserialize(String data, Class<T> eventType) {
        return JSON.parseObject(data, eventType, JSONReader.Feature.FieldBased);
    }
}
