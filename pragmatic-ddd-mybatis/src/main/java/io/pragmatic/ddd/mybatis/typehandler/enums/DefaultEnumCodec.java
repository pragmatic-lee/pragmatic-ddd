package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;

/**
 * 默认 {@link EnumCodec}：按 {@link IEnumValue#getValue()} 取业务 code。
 *
 * @author wizard-lee
 */
public final class DefaultEnumCodec implements EnumCodec {
    @Override
    public Object toCode(Enum<?> e) {
        return ((IEnumValue<?, ?>) e).getValue();
    }
}
