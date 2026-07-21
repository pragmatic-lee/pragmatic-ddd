package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;

/**
 * 默认 {@link EnumCodec}：按 {@link IEnumValue#getValue()} 取业务 code。
 * 对应设计文档 Step 5。
 *
 * <p>v1.1 修订：去掉泛型参数（见 {@link EnumCodec}）。
 */
public final class DefaultEnumCodec implements EnumCodec {
    @Override
    public Object toCode(Enum<?> e) {
        return ((IEnumValue<?, ?>) e).getValue();
    }
}
