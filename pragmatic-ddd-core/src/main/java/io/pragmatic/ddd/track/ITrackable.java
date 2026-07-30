package io.pragmatic.ddd.track;

/**
 * 可追踪对象：有唯一持久化行标识的角色接口。
 * <p>实现此接口表示该对象的实例可以被 {@link TrackedList} 追踪变更：
 * <ul>
 *   <li>实体：{@link #id()} 返回领域 ID（持久化行键）；</li>
 *   <li>独立表值对象：{@link #id()} 返回行键（业务键）。</li>
 * </ul>
 * <p>注意：{@link #id()} 是持久化层的行标识，与 {@code IEntity.getEntityId()}（领域身份）属于不同层次，不可混用。</p>
 *
 * @param <ID> 行标识类型
 *
 * @author wizard-lee
 */
public interface ITrackable<ID> {

    /** 持久化行标识（用于 DELETE/UPDATE 定位行记录）。 */
    ID id();
}
