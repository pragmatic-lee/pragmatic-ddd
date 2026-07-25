package io.pragmatic.ddd.base;

import java.util.Objects;

/**
 * 业务规则违反消息码（Java 17 record，不可变值对象，非枚举），与 VO 领域枚举解耦。
 * 设计上对齐 io.pragmatic.ddd.operation.EntityOperation。
 *
 * 不带 group：code 只是"实体内局部码"，跨实体隔离由"每实体独立 INSTANCE + 上层已知来源"保证。
 *
 * 关于反射：MessageCode 是否为 record 与注册机制无关。构造器反射扫描的是"实体消息子类"
 * 上声明的 static MessageCode 字段；record 仍是普通 Class，getDeclaredFields() /
 * Modifier.isStatic / field.get(null) 均正常工作，注册行为与 OperationRegistry 一致。
 */
public record MessageCode(String localCode, String description) {

    /** 公开工厂，对齐 EntityOperation.of(...) */
    public static MessageCode of(String localCode, String description) {
        return new MessageCode(localCode, description);
    }

    public static MessageCode of(String localCode) {
        return new MessageCode(localCode, "");
    }

    /** 作为 map key 与异常 code 使用；无 group，仅实体内局部码 */
    public String code() {
        return localCode;
    }

    /**
     * equals / hashCode 仅按 localCode（对齐 EntityOperation，description 不参与），
     * 便于后续把 MessageCode 作为 key / 去重。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageCode that)) return false;
        return Objects.equals(localCode, that.localCode);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(localCode);
    }
}
