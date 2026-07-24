package io.pragmatic.ddd.rules;

public interface IParamRule<T> {

    RuleCheckResult isSatisfy(T model);

}
