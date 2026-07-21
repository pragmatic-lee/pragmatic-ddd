package io.pragmatic.ddd.mybatis.typehandler.enums;

/**
 * 枚举 code 编解码 SPI。复杂枚举（多 code 体系、code 需查表 / 加密）可注入自定义 {@code toCode} / {@code normalize}。
 * 默认实现见 {@link DefaultEnumCodec}（按 {@code IEnumValue.getValue()} 取业务 code）。
 * 对应设计文档 Step 5（提案 §5.4）。
 *
 * <p>v1.1 修订：去掉泛型参数，首参放宽为 {@code Enum<?>}，彻底消除 {@code EnumCodec} 的 raw type。
 */
public interface EnumCodec {
    /** 构建索引时从枚举提取 code。 */
    Object toCode(Enum<?> e);

    /** 反序列化时归一化入参（如把 "1" 与 1 当作同一 code），缺省原样返回。 */
    default Object normalize(Object raw) {
        return raw;
    }
}
