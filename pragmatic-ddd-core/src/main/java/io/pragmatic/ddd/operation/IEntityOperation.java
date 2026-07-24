package io.pragmatic.ddd.operation;

/**
 * 实体操作标记接口。
 * <p>对应设计文档 3.2 节：{@link EntityOperation} 实现此轻量标记接口，
 * 供 ArchUnit / 工具与值对象枚举（IValueObject）区分，
 * 从根上规避 action 与值对象（VO）枚举的归类陷阱。</p>
 */
public interface IEntityOperation {

    String code();

    String description();
}
