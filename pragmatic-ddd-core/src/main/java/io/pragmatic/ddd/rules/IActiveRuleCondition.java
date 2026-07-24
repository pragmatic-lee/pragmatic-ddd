package io.pragmatic.ddd.rules;

/**
 * @author lixiaojing
 */
public interface IActiveRuleCondition<T> {
    ActiveStatus status(T model);
}
