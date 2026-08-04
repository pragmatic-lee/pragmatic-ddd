/**
 * 领域驱动设计的基础抽象层。
 *
 * <p>包含：实体核心抽象（AbstractEntity、AggregateRoot、各类标记接口与基类）、
 * 规则违反通知（Notification 模式：IRule、BrokenRule、BrokenRuleObject、BrokenRuleRegistry、MessageCode 及异常）、
 * 实体辅助工具（CompareAndSetInfo）与统一异常体系
 * （PragmaticException、RuleException）。</p>
 *
 * @see io.pragmatic.ddd.rules.EntityRule
 * @see io.pragmatic.ddd.event
 */
package io.pragmatic.ddd.base;
