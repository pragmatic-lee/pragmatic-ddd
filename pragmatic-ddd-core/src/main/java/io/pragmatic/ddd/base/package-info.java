/**
 * 领域驱动设计的基础抽象层。
 *
 * <p>包含：</p>
 * <ul>
 *   <li>实体抽象 —— {@link AbstractEntity}（身份标识、软删标记、审计字段与基于 ID 的等同性）、
 *       {@link AggregateRoot}（规则校验、乐观锁版本号、领域事件收集、操作追踪与工作单元清理）与标记接口 {@link IEntity}</li>
 *   <li>值对象 —— {@link ValueObject}（基于 equalityComponents 的结构相等性）与标记接口
 *       {@link IValueObject}、{@link IEnumValue}、{@link IParamObject}</li>
 *   <li>规则顶层抽象与违反收集（Notification 模式）—— {@link IRule} / {@link BrokenRule} /
 *       {@link BrokenRuleObject} / {@link BrokenRuleRegistry} / {@link MessageCode}
 *       及异常（{@link RuleException}、{@link BrokenRuleException}、{@link BrokenRuleAggregateException}）；
 *       校验项级契约 {@code ICheckRule} / {@code RuleCheckResult} 位于子包 {@code io.pragmatic.ddd.rules}</li>
 *   <li>实体属性计算 —— {@link IEntityPropertyCalculator}（其继承的领域服务标记接口位于 {@code io.pragmatic.ddd.service} 包）</li>
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
