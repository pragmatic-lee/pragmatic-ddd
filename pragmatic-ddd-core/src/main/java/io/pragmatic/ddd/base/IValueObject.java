package io.pragmatic.ddd.base;

/**
 * 值对象标记接口。
 *
 * <p>作为值对象的身份标记，供 {@code visual} 模块通过反射识别"这是值对象"。
 * 仅作标记用途，不承载任何行为契约。</p>
 *
 * <p>需要框架托底的"结构相等性"（by-value equals/hashCode）时，
 * 可让值对象继承 {@link ValueObject} 基类（opt-in）。
 * 不继承、甚至不实现本接口的值对象（纯 POJO）也完全合法。</p>
 *
 * @author lixiaojing10
 * @since 2.0.0
 */
public interface IValueObject {
}
