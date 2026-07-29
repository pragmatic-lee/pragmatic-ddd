package io.pragmatic.ddd.base.id;

/** 生成器产出的标识类型。 */
public enum IdType {
    /** 纯数字 Long。 */
    LONG,
    /** 带前缀 / 格式的 String，依赖 IdGeneratorDefinition.format。 */
    STRING
}
