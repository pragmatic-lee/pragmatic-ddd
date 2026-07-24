package io.pragmatic.ddd.application;

import lombok.Data;

/**
 * 创建地址命令 —— 携带 addressType 字段的命令对象。
 *
 * <p>这里仅用它演示：两个不同命令对象拥有同名字段、相同判断逻辑时，
 * 如何用 v2 方案复用计算逻辑。</p>
 */
@Data
public class CreateAddressCommand {

    /** 地址类型编码（"1" / "2" / 其它） */
    private String addressType;
}
