package io.pragmatic.ddd.base;


import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则违反收集器，可组合也可继承。
 * 既可被子类继承（覆盖 brokenRuleRegistry() 提供注册表），也可被 {@link AggregateRoot} 以组合方式持有（setBrokenRuleRegistry 注入）。
 * 负责规则违反的收集、查询与异常抛出。
 *
 * @author wizard-lee
 */
public class BrokenRuleObject {

    private final List<BrokenRule> brokenRules;
    @Setter
    private BrokenRuleRegistry brokenRuleRegistry;
    private Object source;

    /** 设置触发异常的宿主聚合/实体（组合场景下由 AggregateRoot 注入自身）。 */
    public void setSource(Object source) {
        this.source = source;
    }

    private Object sourceObject() {
        return this.source != null ? this.source : this;
    }


    public BrokenRuleObject() {
        this.brokenRules = new ArrayList<>();
        this.brokenRuleRegistry = this.brokenRuleRegistry();
    }

    /** 子类可覆盖以返回注册表；组合场景下由 setBrokenRuleRegistry 注入。 */
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return this.brokenRuleRegistry;
    }


    /** 返回已收集的规则违反列表（只读）。 */
    public List<BrokenRule> getBrokenRules() {
        return Collections.unmodifiableList(this.brokenRules);
    }

    /** 追加一条规则违反。 */
    public void addBrokenRule(MessageCode code) {
        this.brokenRules.add(new BrokenRule(code.code(),
                this.brokenRuleRegistry.getRuleDescription(code.code())));
    }

    /** 追加一条支持参数格式化的规则违反。 */
    public void addParamBrokenRule(MessageCode code, Object[] params, boolean isAutoFormat) {
        String message = this.brokenRuleRegistry.getRuleDescription(code.code());
        String realMessage = isAutoFormat ? String.format(message, params) : message;
        this.brokenRules.add(new BrokenRule(code.code(), realMessage, params));
    }

    /** 若存在规则违反则抛出单条异常。 */
    public void throwBrokenRuleException() {

        BrokenRuleException brokenRuleException = this.exceptionCause();
        if (brokenRuleException != null) {
            throw brokenRuleException;
        }
    }

    /** 返回首个规则违反对应的单条异常，无违反时返回 null。 */
    public BrokenRuleException exceptionCause() {
        if (!this.getBrokenRules().isEmpty()) {
            BrokenRule brokenRule = this.getBrokenRules().get(0);
            return new BrokenRuleException(brokenRule.getName(),
                    brokenRule.getDescription(),
                    this.sourceObject()
            );
        }
        return null;
    }

    /** 返回聚合全部规则违反的异常，无违反时返回 null。 */
    public BrokenRuleAggregateException aggregateExceptionCause() {
        if (!this.getBrokenRules().isEmpty()) {

            List<BrokenRuleException> brokenRuleExceptions = new ArrayList<>();

            for (BrokenRule message : this.getBrokenRules()) {
                BrokenRuleException brokenRuleException = new BrokenRuleException(message.getName(),
                        message.getDescription(), this.sourceObject());

                brokenRuleExceptions.add(brokenRuleException);
            }
            return new BrokenRuleAggregateException(brokenRuleExceptions);
        }
        return null;
    }

    /** 若存在规则违反则抛出聚合异常。 */
    public void throwBrokenRuleAggregateException() {
        BrokenRuleAggregateException brokenRuleAggregateException = this.aggregateExceptionCause();
        if (brokenRuleAggregateException != null) {
            throw brokenRuleAggregateException;
        }
    }

    /** 清空已收集的规则违反。 */
    public void clearBrokenRules() {
        this.brokenRules.clear();
    }
}
