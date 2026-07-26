package io.pragmatic.ddd.base;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BrokenRuleObject {

    private final List<BrokenRule> brokenRules;
    private final BrokenRuleRegistry brokenRuleRegistry;


    public BrokenRuleObject() {
        this.brokenRules = new ArrayList<>();
        this.brokenRuleRegistry = this.brokenRuleRegistry();
    }

    protected abstract BrokenRuleRegistry brokenRuleRegistry();


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
