package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRule;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.ICheckRule;
import io.pragmatic.ddd.base.MessageCode;
import io.pragmatic.ddd.base.RuleCheckResult;
import io.pragmatic.ddd.operation.OperationRegistry;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EntityRule} 综合单元测试：增删改、激活条件、参数规则、failFast、旧状态懒加载与 reset。
 *
 * @author wizard-lee
 */
class EntityRuleTest {

    // ==================== 注册表 & 实体 ====================

    public static class Registry extends BrokenRuleRegistry {
        public static final MessageCode NAME_EMPTY = MessageCode.of("NAME_EMPTY", "名字不能为空");
        public static final MessageCode PRICE_ZERO = MessageCode.of("PRICE_ZERO", "价格不能为0");
        public static final MessageCode PRICE_EQUAL = MessageCode.of("PRICE_EQUAL", "价格必须等于2");
        public static final MessageCode STATUS_ERROR = MessageCode.of("STATUS_ERROR", "状态必须为1");
        public static final MessageCode NAME_USED = MessageCode.of("NAME_USED", "%s 已被使用");
        public static final MessageCode OLD_NAME_DIFF = MessageCode.of("OLD_NAME_DIFF", "旧名=%s 新名=%s");
        public static final MessageCode SKIP_CODE = MessageCode.of("SKIP_CODE", "跳过码");
        public static final MessageCode R1 = MessageCode.of("R1", "r1");
        public static final MessageCode R2 = MessageCode.of("R2", "r2");
        public static final MessageCode R3 = MessageCode.of("R3", "r3");
        public static final MessageCode R1_NEW = MessageCode.of("R1_NEW", "r1new");
        public static final Registry INSTANCE = new Registry();
    }

    @Data
    static class SampleEntity extends AggregateRoot<Long> {
        private String name = "";
        private Double price = 0.0;
        private int status;

        SampleEntity() {
            this.setEntityId(1L);
        }

        @Override
        protected BrokenRuleRegistry brokenRuleRegistry() {
            return Registry.INSTANCE;
        }

        @Override
        protected OperationRegistry operationRegistry() {
            return null;
        }
    }

    // ==================== 通用规则 ====================

    static class FullRule extends EntityRule<SampleEntity> {
        FullRule() {
            this.init();
        }

        FullRule(boolean failFast) {
            super(failFast);
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> RuleCheckResult.of(e.getName() != null && !e.getName().isEmpty()), Registry.NAME_EMPTY);
            this.addRule(e -> RuleCheckResult.of(e.getPrice() != null && e.getPrice() > 0), Registry.PRICE_ZERO);
            this.addRule(e -> RuleCheckResult.of(e.getPrice() != null && e.getPrice().equals(2.0)), Registry.PRICE_EQUAL);
            this.addRule(e -> RuleCheckResult.of(e.getStatus() == 1), Registry.STATUS_ERROR,
                    m -> m.getStatus() == 1 ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);
            this.addRule(e -> e.getName().equals("allowed")
                            ? RuleCheckResult.pass()
                            : RuleCheckResult.fail(new Object[]{e.getName()}),
                    Registry.NAME_USED,
                    m -> !m.getName().isEmpty() ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);
        }
    }

    // ==================== 激活条件 ====================

    static class InactiveSkipRule extends EntityRule<SampleEntity> {
        InactiveSkipRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> RuleCheckResult.of(false), Registry.SKIP_CODE, m -> ActiveStatus.INACTIVE);
        }
    }

    static class ActiveFailRule extends EntityRule<SampleEntity> {
        ActiveFailRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> RuleCheckResult.of(false), Registry.SKIP_CODE, m -> ActiveStatus.ACTIVE);
        }
    }

    // ==================== BaseRuleValidator / ICheckRuleBuilder ====================

    static class NotEmptyNameValidator extends BaseRuleValidator<SampleEntity> {
        @Override
        protected boolean validate(SampleEntity model) {
            return model.getName() != null && !model.getName().isEmpty();
        }
    }

    static class ValidatorRule extends EntityRule<SampleEntity> {
        ValidatorRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(new NotEmptyNameValidator(), Registry.NAME_EMPTY);
        }
    }

    static class NameUsedBuilder implements ICheckRuleBuilder<SampleEntity> {
        @Override
        public ICheckRule<SampleEntity> rule() {
            return en -> en.getName().equals("used")
                    ? RuleCheckResult.fail(new Object[]{en.getName()})
                    : RuleCheckResult.pass();
        }

        @Override
        public IActiveRuleCondition<SampleEntity> ruleCondition() {
            return m -> m.getName().isEmpty() ? ActiveStatus.INACTIVE : ActiveStatus.ACTIVE;
        }
    }

    static class BuilderRule extends EntityRule<SampleEntity> {
        BuilderRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(new NameUsedBuilder(), Registry.NAME_USED);
        }
    }

    static class ParamAutoFormatRule extends EntityRule<SampleEntity> {
        private final boolean autoFormat;

        ParamAutoFormatRule(boolean autoFormat) {
            this.autoFormat = autoFormat;
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(en -> RuleCheckResult.fail(new Object[]{en.getName()}, autoFormat), Registry.NAME_USED);
        }
    }

    // ==================== appendRule ====================

    static class AppendBaseRule extends EntityRule<SampleEntity> {
        AppendBaseRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> RuleCheckResult.of(true), Registry.R1);
            this.addRule(e -> RuleCheckResult.of(true), Registry.R2);
            this.addRule(e -> RuleCheckResult.of(true), Registry.R3);
        }
    }

    // ==================== 旧状态懒加载 ====================

    static class OldEntityRule extends EntityRule<SampleEntity> {
        final AtomicInteger supplyCount = new AtomicInteger(0);
        boolean sameInstance;
        boolean oldIsNull;
        SampleEntity capturedCurrent;

        OldEntityRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            supplyCount.incrementAndGet();
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> {
                SampleEntity first = this.getOldEntity();
                SampleEntity second = this.getOldEntity();
                sameInstance = (first == second);
                oldIsNull = (first == null);
                capturedCurrent = this.currentEntity();
                return RuleCheckResult.of(true);
            }, Registry.NAME_EMPTY);
        }
    }

    static class InactiveOldRule extends EntityRule<SampleEntity> {
        final AtomicInteger supplyCount = new AtomicInteger(0);

        InactiveOldRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            supplyCount.incrementAndGet();
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> RuleCheckResult.of(true), Registry.NAME_EMPTY, m -> ActiveStatus.INACTIVE);
        }
    }

    static class FailFastOldRule extends EntityRule<SampleEntity> {
        final AtomicInteger supplyCount = new AtomicInteger(0);

        FailFastOldRule() {
            super(true);
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            supplyCount.incrementAndGet();
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> RuleCheckResult.of(false), Registry.NAME_EMPTY);
            this.addRule(e -> {
                this.getOldEntity();
                return RuleCheckResult.of(true);
            }, Registry.PRICE_ZERO);
        }
    }

    static class ParamOldRule extends EntityRule<SampleEntity> {
        final AtomicInteger supplyCount = new AtomicInteger(0);

        ParamOldRule() {
            this.init();
        }

        @Override
        protected SampleEntity supplyOldEntity() {
            supplyCount.incrementAndGet();
            SampleEntity old = new SampleEntity();
            old.setName("old");
            return old;
        }

        @Override
        public void init() {
            this.addRule(e -> {
                SampleEntity old = this.getOldEntity();
                if (old != null && !e.getName().equals(old.getName())) {
                    return RuleCheckResult.fail(new Object[]{old.getName(), e.getName()});
                }
                return RuleCheckResult.pass();
            }, Registry.OLD_NAME_DIFF);
        }
    }

    // ==================== 测试用例 ====================

    @Test
    void satisfiesRule_failFast_stopsAtFirstFailure() {
        SampleEntity entity = new SampleEntity();
        EntityRule<SampleEntity> rule = new FullRule();
        assertThat(rule.satisfiesRule(entity)).isFalse();
        assertThat(entity.getBrokenRules()).hasSize(1);
        assertThat(entity.getBrokenRules().get(0).getName()).isEqualTo(Registry.NAME_EMPTY.code());
    }

    @Test
    void satisfiesRule_nonFailFast_collectsAll() {
        SampleEntity entity = new SampleEntity();
        EntityRule<SampleEntity> rule = new FullRule(false);
        assertThat(rule.satisfiesRule(entity)).isFalse();
        assertThat(entity.getBrokenRules()).hasSize(3);
        assertThat(entity.getBrokenRules()).extracting(BrokenRule::getName)
                .containsExactlyInAnyOrder(Registry.NAME_EMPTY.code(),
                        Registry.PRICE_ZERO.code(), Registry.PRICE_EQUAL.code());
    }

    @Test
    void satisfiesRule_allPass_zeroBroken() {
        SampleEntity entity = new SampleEntity();
        entity.setName("allowed");
        entity.setPrice(2.0);
        EntityRule<SampleEntity> rule = new FullRule();
        assertThat(rule.satisfiesRule(entity)).isTrue();
        assertThat(entity.getBrokenRules()).isEmpty();
    }

    @Test
    void condition_inactive_skipsFailingRule() {
        SampleEntity entity = new SampleEntity();
        EntityRule<SampleEntity> rule = new InactiveSkipRule();
        assertThat(rule.satisfiesRule(entity)).isTrue();
        assertThat(entity.getBrokenRules()).isEmpty();
    }

    @Test
    void condition_active_runsFailingRule() {
        SampleEntity entity = new SampleEntity();
        EntityRule<SampleEntity> rule = new ActiveFailRule();
        assertThat(rule.satisfiesRule(entity)).isFalse();
        assertThat(entity.getBrokenRules().get(0).getName()).isEqualTo(Registry.SKIP_CODE.code());
    }

    @Test
    void addRule_autoFormatTrue_formatsMessage() {
        SampleEntity entity = new SampleEntity();
        entity.setName("bob");
        EntityRule<SampleEntity> rule = new ParamAutoFormatRule(true);
        assertThat(rule.satisfiesRule(entity)).isFalse();
        BrokenRule brokenRule = entity.getBrokenRules().get(0);
        assertThat(brokenRule.getName()).isEqualTo(Registry.NAME_USED.code());
        assertThat(brokenRule.getDescription()).isEqualTo("bob 已被使用");
        assertThat(brokenRule.getExtraData()).containsExactly("bob");
    }

    @Test
    void addRule_autoFormatFalse_keepsRawMessage() {
        SampleEntity entity = new SampleEntity();
        entity.setName("bob");
        EntityRule<SampleEntity> rule = new ParamAutoFormatRule(false);
        assertThat(rule.satisfiesRule(entity)).isFalse();
        assertThat(entity.getBrokenRules().get(0).getDescription()).isEqualTo("%s 已被使用");
    }

    @Test
    void addRule_withBaseRuleValidator_wrapsLogic() {
        SampleEntity empty = new SampleEntity();
        EntityRule<SampleEntity> rule = new ValidatorRule();
        assertThat(rule.satisfiesRule(empty)).isFalse();
        assertThat(empty.getBrokenRules().get(0).getName()).isEqualTo(Registry.NAME_EMPTY.code());

        empty.clearBrokenRules();
        empty.setName("ok");
        assertThat(rule.satisfiesRule(empty)).isTrue();
    }

    @Test
    void addRule_withBuilder_usesInternalCondition() {
        SampleEntity empty = new SampleEntity();
        EntityRule<SampleEntity> skipRule = new BuilderRule();
        assertThat(skipRule.satisfiesRule(empty)).isTrue();

        SampleEntity used = new SampleEntity();
        used.setName("used");
        EntityRule<SampleEntity> failRule = new BuilderRule();
        assertThat(failRule.satisfiesRule(used)).isFalse();
        assertThat(used.getBrokenRules().get(0).getName()).isEqualTo(Registry.NAME_USED.code());
    }

    @Test
    void appendRule_last_addsAtEnd() {
        AppendBaseRule rule = new AppendBaseRule();
        rule.appendRule(e -> RuleCheckResult.of(true), MessageCode.of("APPEND_LAST", "x"),
                Registry.R2, RulePosition.LAST, null);
        List<RuleItem<SampleEntity>> items = rule.allRuleItems();
        assertThat(items).hasSize(4);
        assertThat(items.get(3).getMessageCode().code()).isEqualTo("APPEND_LAST");
    }

    @Test
    void appendRule_before_insertsBeforeRelative() {
        AppendBaseRule rule = new AppendBaseRule();
        rule.appendRule(e -> RuleCheckResult.of(true), MessageCode.of("APPEND_BEFORE", "x"),
                Registry.R2, RulePosition.BEFORE, null);
        List<RuleItem<SampleEntity>> items = rule.allRuleItems();
        assertThat(items).hasSize(4);
        assertThat(items.get(0).getMessageCode().code()).isEqualTo("R1");
        assertThat(items.get(1).getMessageCode().code()).isEqualTo("APPEND_BEFORE");
        assertThat(items.get(2).getMessageCode().code()).isEqualTo("R2");
    }

    @Test
    void appendRule_after_insertsAfterRelative() {
        AppendBaseRule rule = new AppendBaseRule();
        rule.appendRule(e -> RuleCheckResult.of(true), MessageCode.of("APPEND_AFTER", "x"),
                Registry.R2, RulePosition.AFTER, null);
        List<RuleItem<SampleEntity>> items = rule.allRuleItems();
        assertThat(items).hasSize(4);
        assertThat(items.get(1).getMessageCode().code()).isEqualTo("R2");
        assertThat(items.get(2).getMessageCode().code()).isEqualTo("APPEND_AFTER");
        assertThat(items.get(3).getMessageCode().code()).isEqualTo("R3");
    }

    @Test
    void appendRule_relativeNotFound_notInserted() {
        AppendBaseRule rule = new AppendBaseRule();
        rule.appendRule(e -> RuleCheckResult.of(true), MessageCode.of("APPEND_X", "x"),
                MessageCode.of("NOPE", "x"), RulePosition.BEFORE, null);
        assertThat(rule.allRuleItems()).hasSize(3);
    }

    @Test
    void replaceRule_byCode_changesCode() {
        EntityRule<SampleEntity> rule = new FullRule();
        assertThat(rule.findRuleByMessageCode(Registry.PRICE_EQUAL)).isNotNull();
        rule.replaceRule(e -> RuleCheckResult.of(e.getPrice() != null && e.getPrice().equals(2.0)),
                Registry.PRICE_EQUAL, Registry.R1_NEW);
        assertThat(rule.findRuleByMessageCode(Registry.PRICE_EQUAL)).isNull();
        assertThat(rule.findRuleByMessageCode(Registry.R1_NEW)).isNotNull();
    }

    @Test
    void replaceRule_changesMessageCode() {
        EntityRule<SampleEntity> rule = new FullRule();
        assertThat(rule.allRuleItems()).extracting(item -> item.getMessageCode().code())
                .contains(Registry.NAME_USED.code());
        rule.replaceRule(e -> RuleCheckResult.pass(),
                Registry.NAME_USED, Registry.R1_NEW);
        List<RuleItem<SampleEntity>> items = rule.allRuleItems();
        assertThat(items).extracting(item -> item.getMessageCode().code())
                .doesNotContain(Registry.NAME_USED.code())
                .contains(Registry.R1_NEW.code());
    }

    @Test
    void removeRule_removesByMessageCode() {
        EntityRule<SampleEntity> rule = new FullRule();
        assertThat(rule.findRuleByMessageCode(Registry.PRICE_ZERO)).isNotNull();
        rule.removeRule(Registry.PRICE_ZERO);
        assertThat(rule.findRuleByMessageCode(Registry.PRICE_ZERO)).isNull();

        SampleEntity entity = new SampleEntity();
        assertThat(rule.satisfiesRule(entity)).isFalse();
        assertThat(entity.getBrokenRules()).noneMatch(b -> b.getName().equals(Registry.PRICE_ZERO.code()));
        assertThat(entity.getBrokenRules()).anyMatch(b -> b.getName().equals(Registry.NAME_EMPTY.code()));
    }

    @Test
    void findRuleByMessageCode_hitAndMiss() {
        EntityRule<SampleEntity> rule = new FullRule();
        assertThat(rule.findRuleByMessageCode(Registry.NAME_EMPTY)).isNotNull();
        assertThat(rule.findRuleByMessageCode(MessageCode.of("NOPE"))).isNull();
    }

    @Test
    void findRulesByMessageCode_varargs() {
        EntityRule<SampleEntity> rule = new FullRule();
        List<ICheckRule<SampleEntity>> list = rule.findRulesByMessageCode(
                Registry.NAME_EMPTY, Registry.PRICE_ZERO, MessageCode.of("NOPE"));
        assertThat(list).hasSize(2);
    }

    @Test
    void allRuleItems_returnsDefensiveCopy() {
        EntityRule<SampleEntity> rule = new FullRule();
        List<RuleItem<SampleEntity>> items = rule.allRuleItems();
        int size = items.size();
        items.clear();
        assertThat(rule.allRuleItems()).hasSize(size);
    }

    @Test
    void reset_clearsAndReinitializes() {
        EntityRule<SampleEntity> rule = new FullRule();
        rule.removeRule(Registry.PRICE_ZERO);
        assertThat(rule.findRuleByMessageCode(Registry.PRICE_ZERO)).isNull();
        rule.reset();
        assertThat(rule.findRuleByMessageCode(Registry.PRICE_ZERO)).isNotNull();
    }

    @Test
    void getOldEntity_lazyLoadedAndCalledOnce() {
        OldEntityRule rule = new OldEntityRule();
        SampleEntity entity = new SampleEntity();
        assertThat(rule.satisfiesRule(entity)).isTrue();
        assertThat(rule.supplyCount.get()).isEqualTo(1);
        assertThat(rule.sameInstance).isTrue();
    }

    @Test
    void getOldEntity_nullForCreate() {
        OldEntityRule rule = new OldEntityRule();
        SampleEntity entity = new SampleEntity();
        rule.satisfiesRule(entity);
        assertThat(rule.oldIsNull).isTrue();
    }

    @Test
    void currentEntity_returnsValidatingModel() {
        OldEntityRule rule = new OldEntityRule();
        SampleEntity entity = new SampleEntity();
        rule.satisfiesRule(entity);
        assertThat(rule.capturedCurrent).isSameAs(entity);
    }

    @Test
    void inactiveCondition_skipsOldEntityLoad() {
        SampleEntity entity = new SampleEntity();
        InactiveOldRule rule = new InactiveOldRule();
        assertThat(rule.satisfiesRule(entity)).isTrue();
        assertThat(rule.supplyCount.get()).isEqualTo(0);
    }

    @Test
    void multipleValidationRuns_cacheResetsEachTime() {
        OldEntityRule rule = new OldEntityRule();
        SampleEntity entity = new SampleEntity();
        rule.satisfiesRule(entity);
        assertThat(rule.supplyCount.get()).isEqualTo(1);
        rule.supplyCount.set(0);
        rule.satisfiesRule(entity);
        assertThat(rule.supplyCount.get()).isEqualTo(1);
    }

    @Test
    void failFastWithOldEntity_secondRuleNotExecuted() {
        SampleEntity entity = new SampleEntity();
        FailFastOldRule rule = new FailFastOldRule();
        assertThat(rule.satisfiesRule(entity)).isFalse();
        assertThat(rule.supplyCount.get()).isEqualTo(0);
        assertThat(entity.getBrokenRules()).hasSize(1);
        assertThat(entity.getBrokenRules().get(0).getName()).isEqualTo(Registry.NAME_EMPTY.code());
    }

    @Test
    void paramRuleWithOldEntity_loadsOnce() {
        SampleEntity entity = new SampleEntity();
        entity.setName("new");
        ParamOldRule rule = new ParamOldRule();
        rule.satisfiesRule(entity);
        assertThat(rule.supplyCount.get()).isEqualTo(1);
    }
}
