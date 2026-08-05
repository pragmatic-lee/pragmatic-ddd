/**
 * 领域驱动设计的基础抽象层。
 *
 * <p>包含：</p>
 * <ul>
 *   <li>实体抽象 —— {@link AbstractEntity}（身份标识、软删标记、审计字段与基于 ID 的等同性）、
 *       {@link AggregateRoot}（规则校验、乐观锁版本号、领域事件收集、操作追踪与工作单元清理）与标记接口 {@link IEntity}</li>
 *   <li>值对象 —— {@link ValueObject}（基于 equalityComponents 的结构相等性）与标记接口
 *       {@link IValueObject}、{@link IEnumValue}、{@link IParamObject}</li>
 *   <li>规则违反收集（Notification 模式）—— {@link IRule} / {@link ICheckRule} / {@link BrokenRule} /
 *       {@link BrokenRuleObject} / {@link BrokenRuleRegistry} / {@link MessageCode} / {@link RuleCheckResult}
 *       及异常（{@link RuleException}、{@link BrokenRuleException}、{@link BrokenRuleAggregateException}）</li>
 *   <li>领域服务与实体属性计算 —— {@link IDomainService}、{@link IEntityPropertyCalculator}</li>
 *   <li>实体辅助工具 —— {@link CompareAndSetInfo}</li>
 *   <li>统一异常体系 —— {@link PragmaticException}、{@link RuleException}</li>
 *   <li>子包 {@code id} —— 号段模式 ID 生成器体系（{@code IIdGenerator} / {@code IIdSegmentAllocator} /
 *       {@code LongSegmentIdGenerator} / {@code StringSegmentIdGenerator} 等）</li>
 * </ul>
 *
 * @see io.pragmatic.ddd.rules.EntityRule
 * @see io.pragmatic.ddd.event
 * @author wizard-lee
 */
package io.pragmatic.ddd.base;
