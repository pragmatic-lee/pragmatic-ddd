package io.pragmatic.ddd.base.id;

import java.util.List;

/**
 * 唯一标识生成器端口。一个实例对应一个 bizKey（一个独立 ID 空间 / 一个渠道）。
 *
 * @param <T> 标识类型，通常为 Long 或带前缀的 String
 * @author wizard-lee
 */
public interface IIdGenerator<T> {

    /** 业务键（渠道 / 聚合类型），用于在注册中心隔离不同的 ID 空间。 */
    String bizKey();

    /** 生成下一个唯一标识。 */
    T nextId();

    /** 批量生成。 */
    List<T> nextIds(int count);
}
