package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.ICheckRule;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.MessageCode;
import io.pragmatic.ddd.base.RuleCheckResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 实体规则容器 —— 业务不变量的集合。
 *
 * <p>EntityRule 是一个一维的规则列表，不区分"属性级"与"类级"规则。
 * 每条校验项通过 {@link ICheckRule#check} 对模型进行校验，
 * 违规信息通过 {@code BrokenRuleObject} 收集。</p>
 *
 * <p>规则通过 MessageCode 进行运行时增删改（append / replace / remove），
 * 支持 failFast（遇第一条失败即停止）和全量校验两种模式。</p>
 *
 * @param <T> 被校验的模型类型，必须继承 AggregateRoot
 *
 * @author wizard-lee
 */
public abstract class EntityRule<T extends AggregateRoot<?>> implements IRule<T>, IRuleBuild {

    private final List<RuleItem<T>> rules;
    private final AlwaysActiveRuleCondition<T> defaultCondition = new AlwaysActiveRuleCondition<>();
    private final boolean failFast;

    // ========== 旧状态懒加载缓存 ==========

    private T cachedOldEntity;
    private boolean oldEntityLoaded;
    private T validatingEntity;

    /** 构造规则容器（默认 failFast=true）。 */
    public EntityRule() {
        this(true);
    }

    /** 构造规则容器并指定是否 failFast（遇首条失败即停止）。 */
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

    /** 返回全部规则项的副本。 */
    public List<RuleItem<T>> allRuleItems() {
        return new ArrayList<>(this.rules);
    }

    /** 按消息码查找单条规则；未命中返回 null。 */
    public ICheckRule<T> findRuleByMessageCode(MessageCode messageCode) {
        return rules.stream()
                .filter(r -> r.getMessageCode().equals(messageCode))
                .findFirst()
                .map(RuleItem::getRule)
                .orElse(null);
    }

    /** 按多个消息码批量查找规则。 */
    public List<ICheckRule<T>> findRulesByMessageCode(MessageCode... messageCodes) {
        List<ICheckRule<T>> result = new ArrayList<>();
        for (MessageCode messageCode : messageCodes) {
            rules.stream()
                    .filter(r -> r.getMessageCode().equals(messageCode))
                    .findFirst()
                    .ifPresent(r -> result.add(r.getRule()));
        }
        return result;
    }

    // ========== addRule ==========

    /** 追加校验项（使用默认激活条件）。 */
    public void addRule(ICheckRule<T> rule, MessageCode messageCode) {
        this.addRule(rule, messageCode, this.defaultCondition);
    }

    /** 追加校验项并指定激活条件。 */
    public void addRule(ICheckRule<T> rule, MessageCode messageCode, IActiveRuleCondition<T> condition) {
        this.rules.add(new RuleItem<>(rule, messageCode, condition));
    }

    /** 追加校验器规则（取其内部激活条件）。 */
    public void addRule(BaseRuleValidator<T> rule, MessageCode messageCode) {
        IActiveRuleCondition<T> condition =
                Optional.ofNullable(rule.ruleCondition()).orElse(defaultCondition);
        this.rules.add(new RuleItem<>(rule.rule(), messageCode, condition));
    }

    /** 追加校验项构造器（取其内部激活条件）。 */
    public void addRule(ICheckRuleBuilder<T> rule, MessageCode messageCode) {
        IActiveRuleCondition<T> condition =
                Optional.ofNullable(rule.ruleCondition()).orElse(defaultCondition);
        this.rules.add(new RuleItem<>(rule.rule(), messageCode, condition));
    }

    // ========== appendRule ==========

    /** 在参照规则指定位置插入校验项。 */
    public void appendRule(ICheckRule<T> rule,
                           MessageCode appendMessageCode,
                           MessageCode relativeMessageCode,
                           RulePosition position,
                           IActiveRuleCondition<T> condition) {
        RuleItem<T> tRuleItem = new RuleItem<>(rule, appendMessageCode,
                Optional.ofNullable(condition).orElse(this.defaultCondition));
        this.appendRule(this.rules, tRuleItem, relativeMessageCode, position);
    }

    private void appendRule(List<RuleItem<T>> rules, RuleItem<T> rule,
                            MessageCode relativeMessageCode, RulePosition position) {
        if (position == RulePosition.LAST) {
            rules.add(rule);
        } else {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).getMessageCode().equals(relativeMessageCode)) {
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

    /** 替换规则的消息码（使用默认激活条件）。 */
    public void replaceRule(ICheckRule<T> rule, MessageCode replaceMessageCode, MessageCode newMessageCode) {
        this.replaceRule(rule, replaceMessageCode, newMessageCode, this.defaultCondition);
    }

    /** 替换规则的消息码并指定激活条件。 */
    public void replaceRule(ICheckRule<T> rule, MessageCode replaceMessageCode,
                            MessageCode newMessageCode, IActiveRuleCondition<T> condition) {
        for (int i = 0; i < this.rules.size(); i++) {
            if (this.rules.get(i).getMessageCode().equals(replaceMessageCode)) {
                this.rules.set(i, new RuleItem<>(rule, newMessageCode, condition));
                break;
            }
        }
    }

    // ========== removeRule ==========

    /** 按消息码移除规则。 */
    public void removeRule(MessageCode messageCode) {
        this.rules.removeIf(r -> r.getMessageCode().equals(messageCode));
    }

    // ========== satisfiesRule ==========

    /** 遍历全部校验项对模型校验，支持 failFast 与参数化消息。 */
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
            RuleCheckResult result = rule.getRule().check(model);
            if (!result.isSatisfy()) {
                isValid = false;
                if (result.hasParams()) {
                    model.addParamBrokenRule(rule.getMessageCode(), result.getParams(), result.isAutoFormat());
                } else {
                    model.addBrokenRule(rule.getMessageCode());
                }
                if (this.failFast) {
                    break;
                }
            }
        }
        return isValid;
    }

    /** 清空规则并重新初始化。 */
    @Override
    public void reset() {
        this.rules.clear();
        this.init();
    }
}
