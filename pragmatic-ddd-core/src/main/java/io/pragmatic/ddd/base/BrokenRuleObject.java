package io.pragmatic.ddd.base;


import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则违反收集器（可组合、可继承）。
 *
 * <p>对应重构计划 3.1 节：去抽象化，改为可组合收集器。
 * 既可被子类继承（覆盖 {@link #brokenRuleRegistry()} 提供注册表），
 * 也可被 {@code AggregateRoot} 以组合方式持有（通过 {@link #setBrokenRuleRegistry} 注入注册表）。</p>
 */
public class BrokenRuleObject {

    private final List<BrokenRule> brokenRules;
    @Setter
    private BrokenRuleRegistry brokenRuleRegistry;


    public BrokenRuleObject() {
        this.brokenRules = new ArrayList<>();
        this.brokenRuleRegistry = this.brokenRuleRegistry();
    }

    /** 子类可覆盖以返回注册表；组合场景下由 {@link #setBrokenRuleRegistry} 注入。 */
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return this.brokenRuleRegistry;
    }


    public List<BrokenRule> getBrokenRules() {
        return Collections.unmodifiableList(this.brokenRules);
    }

    public void addBrokenRule(MessageCode code) {
        this.brokenRules.add(new BrokenRule(code.code(),
                this.brokenRuleRegistry.getRuleDescription(code.code())));
    }

    public void addParamBrokenRule(MessageCode code, Object[] params, boolean isAutoFormat) {
        String message = this.brokenRuleRegistry.getRuleDescription(code.code());
        String realMessage = isAutoFormat ? String.format(message, params) : message;
        this.brokenRules.add(new BrokenRule(code.code(), realMessage, params));
    }

    public void throwBrokenRuleException() {

        BrokenRuleException brokenRuleException = this.exceptionCause();
        if (brokenRuleException != null) {
            throw brokenRuleException;
        }
    }

    public BrokenRuleException exceptionCause() {
        if (!this.getBrokenRules().isEmpty()) {
            BrokenRule brokenRule = this.getBrokenRules().get(0);
            return new BrokenRuleException(brokenRule.getName(),
                    brokenRule.getDescription(),
                    null,
                    brokenRule.getExtraData()
            );
        }
        return null;
    }

    public BrokenRuleAggregateException aggregateExceptionCause() {
        if (!this.getBrokenRules().isEmpty()) {

            List<BrokenRuleException> brokenRuleExceptions = new ArrayList<>();

            for (BrokenRule message : this.getBrokenRules()) {
                BrokenRuleException brokenRuleException = new BrokenRuleException(message.getName(),
                        message.getDescription(), null, message.getExtraData());

                brokenRuleExceptions.add(brokenRuleException);
            }
            return new BrokenRuleAggregateException(brokenRuleExceptions);
        }
        return null;
    }

    public void throwBrokenRuleAggregateException() {
        BrokenRuleAggregateException brokenRuleAggregateException = this.aggregateExceptionCause();
        if (brokenRuleAggregateException != null) {
            throw brokenRuleAggregateException;
        }
    }

    public void clearBrokenRules() {
        this.brokenRules.clear();
    }
}
