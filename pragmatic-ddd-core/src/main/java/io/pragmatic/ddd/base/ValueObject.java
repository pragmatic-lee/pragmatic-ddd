package io.pragmatic.ddd.base;

import java.util.Arrays;

/**
 * 值对象基类（可选继承），基于 equalityComponents() 提供结构相等性。
 * 不可变性等由使用者通过全参构造器 + 构造期校验保证；本基类完全可选。
 *
 * @author wizard-lee
 */
public abstract class ValueObject implements IValueObject {

    /**
     * 参与相等性判定的成分（顺序敏感）。
     * <p>子类声明"拿哪几个值来判定我和别人相等"，框架据此统一生成
     * {@code equals} / {@code hashCode}。不一定是全部字段——
     * 仅返回语义上真正决定相等性的成分即可。</p>
     *
     * @return 相等性成分数组，不可为 null
     */
    protected abstract Object[] equalityComponents();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Arrays.equals(equalityComponents(), ((ValueObject) o).equalityComponents());
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(equalityComponents());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + Arrays.toString(equalityComponents());
    }
}
