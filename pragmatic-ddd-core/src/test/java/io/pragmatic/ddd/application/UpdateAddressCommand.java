package io.pragmatic.ddd.application;

import lombok.Data;

/**
 * 修改地址命令 —— 同样携带 addressType 字段的命令对象。
 *
 * <p>与 {@link CreateAddressCommand} 类型不同，但 addressType 的判断逻辑完全相同。
 * 旧写法被迫在各自方法里重复 if-else；v2 方案复用同一个 Calculator。</p>
 */
@Data
public class UpdateAddressCommand {

    /** 地址类型编码（"1" / "2" / 其它） */
    private String addressType;
}
