package io.pragmatic.ddd.base;

import java.util.Arrays;

/**
 * 值对象基类（可选继承）。
 *
 * <p>仅解决值对象最本质的"结构相等性"问题：两个值对象在
 * {@link #equalityComponents()} 返回的成分全相等时即视为相等。
 * 不可变性、自校验等由使用者通过"全参构造器 + 构造期校验"的惯用法保证，
 * 框架不强行约束。</p>
 *
 * <p>不需要值对象能力的场景（如纯 POJO）可仅实现 {@link IValueObject} 标记接口，
 * 或什么都不实现——本基类为完全可选（opt-in）。</p>
 *
 * <p>相等性采用运行时类型严格判断（{@code getClass() != o.getClass()}），
 * 避免值对象因继承产生的相等性对称破坏；若未来需要跨子类相等，可升级为
 * Bloch 推荐的 {@code canEqual} 模式。</p>
 *
 * @see IValueObject
 * @since 2.2.0
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
