/**
 * 领域驱动设计的基础抽象层。
 *
 * <h2>包内容组织</h2>
 *
 * <h3>实体核心抽象</h3>
 * <ul>
 *   <li>{@link io.pragmatic.ddd.base.AbstractEntity EntityBase} — 实体基类，提供实体身份标识、事件收集、动作追踪、乐观锁版本控制等能力</li>
 *   <li>{@link io.pragmatic.ddd.base.DomainEntity @DomainEntity} — 标记领域实体的注解，含聚合根、限界上下文等元数据</li>
 *   <li>{@link io.pragmatic.ddd.base.IEntity IEntity} — 实体标识接口</li>
 *   <li>{@link io.pragmatic.ddd.base.AggregateRoot AggregateRoot} — 聚合根基类，继承 EntityBase，IRepository 编译期约束入口</li>
 *   <li>{@link io.pragmatic.ddd.base.IDomainService IDomainService} — 领域服务标记接口</li>
 *   <li>{@link io.pragmatic.ddd.base.IValueObject IValueObject} — 值对象标记接口</li>
 *   <li>{@link io.pragmatic.ddd.base.IParamObject IParamObject} — 参数对象标记接口</li>
 *   <li>{@link io.pragmatic.ddd.base.IEnumValue IEnumValue} — 枚举值对象标记接口，承载业务 code(getValue)、展示名(getName)与描述(getDesc)</li>
 * </ul>
 *
 * <h3>规则违反通知（Notification Pattern）</h3>
 * <p>实体自校验失败时的违规信息收集与传递机制。参考 Martin Fowler 的 Notification 模式。</p>
 * <ul>
 *   <li>{@link io.pragmatic.ddd.base.IRule IRule} — 规则接口，核心校验合约</li>
 *   <li>{@link io.pragmatic.ddd.base.BrokenRule BrokenRule} — 单条规则违反的值对象（名称、描述、属性、扩展数据）</li>
 *   <li>{@link io.pragmatic.ddd.base.BrokenRuleObject BrokenRuleObject} — 规则违反的收集、查询与异常抛出，提供 validate(IRule) 校验入口</li>
 *   <li>{@link io.pragmatic.ddd.base.BrokenRuleMessage BrokenRuleMessage} — 规则消息模板注册表（messageKey → 描述模板）</li>
 *   <li>{@link io.pragmatic.ddd.base.BrokenRuleException BrokenRuleException} — 单条规则违反时抛出的运行时异常</li>
 *   <li>{@link io.pragmatic.ddd.base.BrokenRuleAggregateException BrokenRuleAggregateException} — 多条规则违反时聚合抛出的异常</li>
 *   <li>{@link io.pragmatic.ddd.base.EmptyBrokenRule EmptyBrokenRule} — BrokenRule 的空对象（Null Object Pattern）</li>
 * </ul>
 *
 * <h3>实体辅助工具</h3>
 * <ul>
 *   <li>{@link io.pragmatic.ddd.base.CompareAndSetInfo CompareAndSetInfo} — CAS 比较结果封装</li>
 * </ul>
 *
 * <h2>典型的实体继承链</h2>
 * <pre>{@code
 * BrokenRuleObject                  ← 规则违反收集能力
 *   └── EntityBase<T>              ← 实体身份 + 事件/动作收集 + 乐观锁
 *         └── YourEntity           ← 具体领域实体
 * }</pre>
 *
 * @see io.pragmatic.ddd.rules.EntityRule
 * @see io.pragmatic.ddd.event
 * @since 2.0.0
 */
package io.pragmatic.ddd.base;
