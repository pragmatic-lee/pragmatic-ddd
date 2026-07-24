package io.pragmatic.ddd.base;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BrokenRuleObject {

    private final List<BrokenRule> brokenRules;
    private final BrokenRuleMessage brokenRuleMessage;

    private static final EmptyBrokenRule emptyBrokenRule = new EmptyBrokenRule();


    public BrokenRuleObject() {
        this.brokenRules = new ArrayList<>();
        this.brokenRuleMessage = this.getBrokenRuleMessages();
    }

    protected abstract BrokenRuleMessage getBrokenRuleMessages();


    /**
     * 使用指定的规则集合执行校验。
     * 校验失败时，规则违反信息会通过 {@link #addBrokenRule} 收集到本对象中，
     * 后续可通过 {@link #throwBrokenRuleException()} 或 {@link #exceptionCause()} 获取。
     *
     * <p>典型用法（Application Service 中注入规则）：</p>
     * <pre>{@code
     *   @Autowired
     *   private OrderCreationRule creationRule;
     *
     *   public void createOrder(Order order) {
     *       if (!order.validate(creationRule)) {
     *           throw order.exceptionCause();
     *       }
     *       repository.save(order);
     *   }
     * }</pre>
     *
     * @param rule 满足 IRule 约束的规则对象（如 EntityRule），为 null 时视为校验通过
     * @return true 表示通过校验，false 表示存在规则违反
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean satisfiesRule(IRule<?> rule) {
        return rule != null && ((IRule) rule).satisfiesRule(this);
    }


    public List<BrokenRule> getBrokenRules() {
        return Collections.unmodifiableList(this.brokenRules);
    }

    public void addBrokenRule(String messageKey) {
        String message = this.brokenRuleMessage.getRuleDescription(messageKey);
        BrokenRule rule = new BrokenRule(messageKey, message);
        this.brokenRules.add(rule);
    }

    public void addBrokenRule(String messageKey, String property) {
        String message = this.brokenRuleMessage.getRuleDescription(messageKey);
        BrokenRule rule = new BrokenRule(messageKey, message, property);
        this.brokenRules.add(rule);
    }

    public void addBrokenRule(String messageKey, String property, String alias) {
        String message = this.brokenRuleMessage.getRuleDescription(messageKey);
        BrokenRule rule = new BrokenRule(messageKey, message, property, alias, null);
        this.brokenRules.add(rule);
    }

    public void addParamBrokenRule(String messageKey, Object[] params, boolean isAutoFormat) {
        this.addParamBrokenRule(messageKey, "", params, "", isAutoFormat);
    }

    public void addParamBrokenRule(String messageKey, String property, Object[] params,
                                   String alias,
                                   boolean isAutoFormat) {

        final String message = this.brokenRuleMessage.getRuleDescription(messageKey);
        String realMessage;

        if (isAutoFormat) {
            realMessage = String.format(message, params);
        } else {
            realMessage = message;
        }
        final BrokenRule rule = new BrokenRule(messageKey, realMessage, property, alias, params);
        this.brokenRules.add(rule);


    }

    public BrokenRule findBrokenRule(String property) {
        BrokenRule rule = null;
        for (BrokenRule b : this.brokenRules) {
            if (b.getProperty().equals(property)) {
                rule = b;
                break;
            }
        }
        if (rule == null) {
            return emptyBrokenRule;
        }
        return rule;
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

    public void throwBrokeRuleAggregateException() {
        BrokenRuleAggregateException brokenRuleAggregateException = this.aggregateExceptionCause();
        if (brokenRuleAggregateException != null) {
            throw brokenRuleAggregateException;
        }
    }

    public void clearBrokenRules() {
        this.brokenRules.clear();
    }
}
