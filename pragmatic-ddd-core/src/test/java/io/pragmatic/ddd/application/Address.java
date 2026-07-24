package io.pragmatic.ddd.application;

import lombok.Data;

/**
 * 测试用实体 —— 仅用于演示 v3 的"实体计算上下文"。
 *
 * <p>真实项目里它应是聚合根（继承 {@code AggregateRoot}），这里只保留演示所需的字段。</p>
 */
@Data
public class Address {

    /** 实体当前已保存的地址类型（修改场景的"旧值"） */
    private String addressType;
}
