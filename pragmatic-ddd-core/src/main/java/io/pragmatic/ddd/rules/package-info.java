/**
 * 聚合内规则校验体系。
 *
 * <p>提供基于 Notification 模式的可组合规则校验能力，供业务聚合（继承 {@code io.pragmatic.ddd.base.AggregateRoot}）
 * 装配并执行不变量校验。包含：</p>
 * <ul>
 *   <li>校验项级契约 —— {@link ICheckRule}（对单条不变量做校验，返回 {@link RuleCheckResult}）及其结果
 *       {@link RuleCheckResult}（携带动态消息参数与自动格式化控制）</li>
 *   <li>规则装配 —— {@link RuleItem}（校验项 + 消息码 + 生效条件 + 启用开关的载体）、
 *       {@link IRuleBuild}（聚合级规则构建入口）、
 *       {@link EntityRule}（聚合规则容器，实现 {@code io.pragmatic.ddd.base.IRule}，驱动校验并收集违规）</li>
 *   <li>生效条件与位置 —— {@link IActiveRuleCondition} / {@link AlwaysActiveRuleCondition}（规则是否生效的判定）、
 *       {@link ActiveStatus}（启用/停用状态）、{@link RulePosition}（校验执行时机）</li>
 * </ul>
 *
 * <p>规则违规通过 {@code io.pragmatic.ddd.base} 包的 Notification 基础设施
 * （{@code BrokenRule} / {@code MessageCode} / 异常体系）上报，本包不重复定义违规收集能力。</p>
 *
 * @see io.pragmatic.ddd.base.AggregateRoot
 * @see io.pragmatic.ddd.base.IRule
 * @author wizard-lee
 */
package io.pragmatic.ddd.rules;
