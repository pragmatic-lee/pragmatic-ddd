package io.pragmatic.ddd.event.spi;

/**
 * 事件序列化端口，负责事件对象与字符串之间的序列化/反序列化。
 *
 * @author wizard-lee
 */
public interface IEventSerializer {

    /** 将事件序列化为字符串。 */
    <T> String serialize(T event);

    /** 按事件类型将字符串反序列化为事件对象。 */
    <T> T deserialize(String data, Class<T> eventType);
}
