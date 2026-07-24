package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.BrokenRuleObject;
import io.pragmatic.ddd.base.IRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 实体规则容器 —— 业务不变量的集合。
 *
 * <p>EntityRule 是一个一维的规则列表，不区分"属性级"与"类级"规则。
 * 每条规则通过 {@link IRule#satisfiesRule} 对模型进行校验，
 * 违规信息通过 {@code BrokenRuleObject} 收集。</p>
 *
 * <p>规则可以通过 messageKey 进行运行时增删改（append / replace / remove），
 * 支持 failFast（遇第一条失败即停止）和全量校验两种模式。</p>
 *
 * @param <T> 被校验的模型类型，必须继承 BrokenRuleObject
 */
public abstract class EntityRule<T extends BrokenRuleObject> implements IRule<T>, IRuleBuild {

    private final List<RuleItem<T>> rules;
    private final AlwaysActiveRuleCondition<T> defaultCondition = new AlwaysActiveRuleCondition<>();
    private final boolean failFast;

    // ========== 旧状态懒加载缓存 ==========

    private T cachedOldEntity;
    private boolean oldEntityLoaded;
    private T validatingEntity;

    public EntityRule() {
        this(true);
    }

    public EntityRule(boolean failFast) {
        this.failFast = failFast;
        this.rules = new ArrayList<>();
    }

    // ========== 旧状态懒加载 ==========

    /**
     * 获取旧实体数据。
     * <p>规则在 {@link #init()} 中通过 {@code this.getOldEntity()} 按需调用。</p>
     * <p>首次调用触发 {@link #supplyOldEntity()}，后续返回缓存值。</p>
     *
     * @return 修改前的实体快照，不存在时（如创建操作）为 null
     */
    protected T getOldEntity() {
        if (!this.oldEntityLoaded) {
            this.cachedOldEntity = this.supplyOldEntity();
            this.oldEntityLoaded = true;
        }
        return this.cachedOldEntity;
    }

    /**
     * 供应旧实体数据。
     * <p>由 {@link #getOldEntity()} 懒加载触发。子类必须实现。</p>
     * <p>可通过 {@link #currentEntity()} 获取当前被校验的实体。</p>
     *
     * @return 修改前的实体快照，不存在时返回 null
     */
    protected abstract T supplyOldEntity();

    /**
     * 获取当前被校验的实体。
     * <p>子类在 {@link #supplyOldEntity()} 中通过此方法获取当前实体。</p>
     *
     * @return 当前正在被校验的实体
     */
    protected T currentEntity() {
        return this.validatingEntity;
    }

    // ========== 查询 ==========

    public List<RuleItem<T>> allRuleItems() {
        return new ArrayList<>(this.rules);
    }

    public IRule<T> findRuleByMessageKey(String messageKey) {
        return rules.stream()
                .filter(r -> r.getMessageKey().equals(messageKey))
                .findFirst()
                .map(RuleItem::getRule)
                .orElse(null);
    }

    public List<IRule<T>> findRulesByMessageKey(String... messageKeys) {
        List<IRule<T>> result = new ArrayList<>();
        for (String messageKey : messageKeys) {
            IRule<T> rule = this.findRuleByMessageKey(messageKey);
            if (rule != null) {
                result.add(rule);
            }
        }
        return result;
    }

    // ========== addRule ==========

    public void addRule(IRule<T> rule, String messageKey) {
        this.addRule(rule, messageKey, this.defaultCondition);
    }

    public void addRule(IRule<T> rule, String messageKey, IActiveRuleCondition<T> condition) {
        this.rules.add(new RuleItem<>(rule, messageKey, "", condition));
    }

    public void addRule(BaseRuleValidator<T> rule, String messageKey) {
        IActiveRuleCondition<T> condition =
                Optional.ofNullable(rule.ruleCondition()).orElse(defaultCondition);
        this.rules.add(new RuleItem<>(rule.rule(), messageKey, "", condition));
    }

    // ========== addParamRule ==========

    public void addParamRule(IParamRule<T> paramRule, String messageKey,
                             IActiveRuleCondition<T> condition) {
        this.rules.add(new RuleItem<>(paramRule, messageKey, "", condition));
    }

    public void addParamRule(IParamRule<T> paramRule, String messageKey) {
        this.addParamRule(paramRule, messageKey, this.defaultCondition);
    }

    public void addParamRule(IParamRuleBuilder<T> paramRule, String messageKey) {
        IActiveRuleCondition<T> condition =
                Optional.ofNullable(paramRule.ruleCondition()).orElse(defaultCondition);
        this.rules.add(new RuleItem<>(paramRule.rule(), messageKey, "", condition));
    }

    // ========== appendRule ==========

    public void appendRule(IRule<T> rule,
                           String appendMessageKey,
                           String relativeMessageKey,
                           RulePosition position,
                           IActiveRuleCondition<T> condition) {
        RuleItem<T> tRuleItem = new RuleItem<>(rule, appendMessageKey, "",
                Optional.ofNullable(condition).orElse(this.defaultCondition));
        this.appendRule(this.rules, tRuleItem, relativeMessageKey, position);
    }

    public void appendParamRule(IParamRule<T> rule,
                                    String appendMessageKey,
                                    String relativeMessageKey,
                                    RulePosition position,
                                    IActiveRuleCondition<T> condition) {
        RuleItem<T> tRuleItem = new RuleItem<>(rule, appendMessageKey, "",
                Optional.ofNullable(condition).orElse(this.defaultCondition));
        this.appendRule(this.rules, tRuleItem, relativeMessageKey, position);
    }

    private void appendRule(List<RuleItem<T>> rules, RuleItem<T> rule,
                            String relativeMessageKey, RulePosition position) {
        if (position == RulePosition.LAST) {
            rules.add(rule);
        } else {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).getMessageKey().equals(relativeMessageKey)) {
                    if (position == RulePosition.BEFORE) {
                        rules.add(i, rule);
                    } else {
                        rules.add(i + 1, rule);
                    }
                    break;
                }
            }
        }
    }

    // ========== replaceRule ==========

    public void replaceRule(IRule<T> rule, String replaceMessageKey, String newMessageKey) {
        this.replaceRule(rule, replaceMessageKey, newMessageKey, this.defaultCondition);
    }

    public void replaceRule(IRule<T> rule, String replaceMessageKey,
                            String newMessageKey, IActiveRuleCondition<T> condition) {
        for (int i = 0; i < this.rules.size(); i++) {
            if (this.rules.get(i).getMessageKey().equals(replaceMessageKey)) {
                this.rules.set(i, new RuleItem<>(rule, newMessageKey, "", condition));
                break;
            }
        }
    }

    public void replaceParamRule(IParamRule<T> paramRule,
                                     String replaceMessageKey, String newMessageKey,
                                     IActiveRuleCondition<T> condition) {
        for (int i = 0; i < this.rules.size(); i++) {
            if (this.rules.get(i).getMessageKey().equals(replaceMessageKey)) {
                this.rules.set(i, new RuleItem<>(paramRule, newMessageKey, "", condition));
                break;
            }
        }
    }

    public void replaceParamRule(IParamRule<T> paramRule,
                                     String replaceMessageKey, String newMessageKey) {
        this.replaceParamRule(paramRule, replaceMessageKey, newMessageKey, this.defaultCondition);
    }

    // ========== removeRule ==========

    public void removeRule(String messageKey) {
        this.rules.removeIf(r -> r.getMessageKey().equals(messageKey));
    }

    // ========== satisfiesRule ==========

    @Override
    public boolean satisfiesRule(T model) {
        // 每次校验重置懒加载缓存
        this.validatingEntity = model;
        this.cachedOldEntity = null;
        this.oldEntityLoaded = false;

        boolean isValid = true;
        for (RuleItem<T> rule : this.rules) {
            if (rule.getCondition().status(model) == ActiveStatus.INACTIVE) {
                continue;
            }
            if (rule.getParamRule() != null) {
                RuleCheckResult result = rule.getParamRule().isSatisfy(model);
                if (!result.isSatisfy()) {
                    isValid = false;
                    model.addParamBrokenRule(rule.getMessageKey(), "",
                            result.getParams(), rule.getAlias(), result.isAutoFormat());
                    if (this.failFast) {
                        break;
                    }
                }
            } else if (rule.getRule() != null) {
                if (!rule.getRule().satisfiesRule(model)) {
                    isValid = false;
                    model.addBrokenRule(rule.getMessageKey(), rule.getAlias());
                    if (this.failFast) {
                        break;
                    }
                }
            }
        }
        return isValid;
    }

    @Override
    public void reset() {
        this.rules.clear();
        this.init();
    }
}
