package io.pragmatic.ddd.base;

import java.util.Objects;

/**
 * 规则违反消息码（Java 17 record，不可变值对象）。
 * 作为消息表 key 与异常 code，仅按 localCode 判定相等。
 *
 * @author wizard-lee
 */
public record MessageCode(String localCode, String description) {

    /** 以局部码与描述创建消息码。 */
    public static MessageCode of(String localCode, String description) {
        return new MessageCode(localCode, description);
    }

    /** 以局部码创建消息码（描述为空）。 */
    public static MessageCode of(String localCode) {
        return new MessageCode(localCode, "");
    }

    /** 返回局部码，作为 map key 与异常 code。 */
    public String code() {
        return localCode;
    }

    /** 仅按 localCode 判定相等，便于作为 key / 去重。 */
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
