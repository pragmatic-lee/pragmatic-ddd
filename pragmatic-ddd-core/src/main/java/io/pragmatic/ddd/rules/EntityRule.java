package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.MessageCode;
import io.pragmatic.ddd.rules.ICheckRule;
import io.pragmatic.ddd.rules.RuleCheckResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
 * <p>校验项接收「新模型」与「旧模型」双参数，EntityRule 自身不持有任何
 * per-call 可变状态，因此实例可作为单例（如 Spring Bean）在多线程环境下安全共享。
 * 需要新旧对比的子类覆盖 {@link #requireOldEntity()} 与 {@link #supplyOldEntity(AggregateRoot)}。</p>
 *
 * @param <T> 被校验的模型类型，必须继承 AggregateRoot
 *
 * @author wizard-lee
 */
public abstract class EntityRule<T extends AggregateRoot<?>> implements IRule<T>, IRuleBuild {

    private final List<RuleItem<T>> rules;
    private final AlwaysActiveRuleCondition<T> defaultCondition = new AlwaysActiveRuleCondition<>();
    private final boolean failFast;

    /** 构造规则容器（默认 failFast=true）。 */
    public EntityRule() {
        this(true);
    }

    /** 构造规则容器并指定是否 failFast（遇首条失败即停止）。 */
    public EntityRule(boolean failFast) {
        this.failFast = failFast;
        this.rules = new ArrayList<>();
    }

    // ========== 单参数适配 ==========

    /** 将单参数校验逻辑适配为双参数校验项，用于不关心旧实体的规则。 */
    public static <T> ICheckRule<T> of(Function<T, RuleCheckResult> singleArgRule) {
        return (newModel, oldModel) -> singleArgRule.apply(newModel);
    }

    // ========== 旧实体供应 ==========

    /**
     * 是否需要加载旧实体。
     * <p>默认 false，即不触发任何旧实体查询。存在新旧对比规则的子类需覆盖为 true。</p>
     */
    protected boolean requireOldEntity() {
        return false;
    }

    /**
     * 供应旧实体数据。
     * <p>仅在 {@link #requireOldEntity()} 返回 true 时，由每次 satisfiesRule 调用一次。</p>
     *
     * @param currentModel 当前被校验的模型
     * @return 修改前的实体快照，不存在时返回 null
     */
    protected T supplyOldEntity(T currentModel) {
        return null;
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
        // per-call 状态：局部变量，天然线程隔离
        T oldModel = this.loadOldEntity(model);

        boolean isValid = true;
        for (RuleItem<T> rule : this.rules) {
            // 第一重：code 级开关（外部动态配置决定是否启用该规则）
            if (rule.getCondition().switchStatus(rule.getMessageCode()) == ActiveStatus.INACTIVE) {
                continue;
            }
            // 第二重：模型级条件（基于模型内容 / 新旧对比决定是否参与校验）
            if (rule.getCondition().status(model, oldModel) == ActiveStatus.INACTIVE) {
                continue;
            }
            RuleCheckResult result = rule.getRule().check(model, oldModel);
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

    private T loadOldEntity(T model) {
        if (!this.requireOldEntity()) {
            return null;
        }
        return this.supplyOldEntity(model);
    }

    /** 清空规则并重新初始化。 */
    @Override
    public void reset() {
        this.rules.clear();
        this.init();
    }
}
