package io.pragmatic.ddd.rule;

import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.rules.*;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * EntityRule 旧状态懒加载功能测试。
 *
 * <p>覆盖场景：零查询、一次加载、缓存、创建操作、条件跳过、failFast、reset</p>
 */
public class EntityRuleWithOldEntityTest {

    // ==================== 测试用例 ====================

    /** TC01: 没有规则调 getOldEntity → supplyOldEntity 永不执行 */
    @Test
    public void noRuleCallsGetOldEntity_supplyNeverCalled() {
        NoOldCallRule rule = new NoOldCallRule();
        TestEntity entity = new TestEntity();
        entity.setName("test");

        assertTrue(rule.satisfiesRule(entity));
        assertEquals(0, rule.supplyInvokeCount.get());
    }

    /** TC02: 多条规则调 getOldEntity → supplyOldEntity 只执行一次 */
    @Test
    public void multipleRulesCallGetOldEntity_supplyCalledOnce() {
        MultiOldCallRule rule = new MultiOldCallRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setName("newValue");
        entity.setCode("code2");

        rule.satisfiesRule(entity);
        assertEquals(1, rule.supplyInvokeCount.get());
    }

    /** TC03: 多次调 getOldEntity 返回同一缓存实例 */
    @Test
    public void getOldEntity_returnsCachedInstance() {
        CacheCheckRule rule = new CacheCheckRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setName("newValue");

        rule.satisfiesRule(entity);
        assertTrue("两次 getOldEntity 应返回同一对象", rule.sameCacheInstance);
    }

    /** TC04: 创建场景（oldEntity = null）→ getOldEntity 返回 null */
    @Test
    public void nullOldEntity_returnsNullForCreateOp() {
        NullOldEntityRule rule = new NullOldEntityRule();
        TestEntity entity = new TestEntity();

        rule.satisfiesRule(entity);
        assertTrue("创建操作 getOldEntity 应返回 null", rule.oldEntityWasNull);
    }

    /** TC05: 条件跳过时 getOldEntity 不触发 */
    @Test
    public void inactiveRules_skipLoad() {
        InactiveConditionRule rule = new InactiveConditionRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setStatus(0); // 条件要求 status=1 才激活

        rule.satisfiesRule(entity);
        assertEquals(0, rule.supplyInvokeCount.get());
    }

    /** TC06: 混合规则 */
    @Test
    public void mixedRules_allExecuteCorrectly() {
        MixedRule rule = new MixedRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setName("test");
        entity.setCode("code");

        assertTrue(rule.satisfiesRule(entity));
        assertEquals(1, rule.supplyInvokeCount.get());
    }

    /** TC07: failFast 模式 + 旧状态规则 */
    @Test
    public void failFastWithOldEntity() {
        FailFastRule rule = new FailFastRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setName("must_fail");

        assertFalse(rule.satisfiesRule(entity));
        assertEquals(1, entity.getBrokenRules().size());
    }

    /** TC08: 多次校验时缓存各自独立 */
    @Test
    public void multipleValidationRuns_cacheResetsEachTime() {
        MultiRunRule rule = new MultiRunRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setName("test");

        // 第一次校验
        rule.satisfiesRule(entity);
        assertEquals(1, rule.supplyInvokeCount.get());

        // 第二次校验（缓存应重置）
        rule.satisfyCount = 0;
        rule.supplyInvokeCount.set(0);
        rule.satisfiesRule(entity);
        assertEquals(1, rule.supplyInvokeCount.get());
    }

    /** TC09: 参数化规则 + getOldEntity */
    @Test
    public void paramRuleWithOldEntity() {
        ParamRuleWithOldRule rule = new ParamRuleWithOldRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setName("changed");

        rule.satisfiesRule(entity);
        assertEquals(1, rule.supplyInvokeCount.get());
    }

    /** TC10: reset 后仍能正常使用 */
    @Test
    public void resetTest() {
        ResetTestRule rule = new ResetTestRule("oldValue");
        TestEntity entity = new TestEntity();
        entity.setName("test");

        rule.satisfiesRule(entity);
        assertTrue(rule.supplyInvokeCount.get() >= 1);

        rule.reset();
        rule.supplyInvokeCount.set(0);

        rule.satisfiesRule(entity);
        assertTrue("reset 后旧状态功能应正常", rule.supplyInvokeCount.get() >= 1);
    }

    // ==================== 辅助实体 ====================

    static class TestEntity extends AbstractEntity<Long> {

        private String name;
        private String code;
        private int status;

        public TestEntity() {
            this.setEntityId(1L);
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }

        @Override
        protected BrokenRuleMessage getBrokenRuleMessages() {
            return new TestBrokenRuleMessage();
        }

        @Override
        protected OperationRegistry entityActions() {
            return null;
        }
    }

    static class TestBrokenRuleMessage extends BrokenRuleMessage {

        static final String NAME_ERROR = "NAME_ERROR";
        static final String CODE_ERROR = "CODE_ERROR";
        static final String BOTH_ERROR = "BOTH_ERROR";

        @Override
        protected void populateMessage() {
            getMessages().put(NAME_ERROR, "名字错误");
            getMessages().put(CODE_ERROR, "代号错误");
            getMessages().put(BOTH_ERROR, "旧值与新值不一致");
        }
    }

    // ==================== 测试用 EntityRule 子类 ====================

    /** TC01: 没有规则调 getOldEntity */
    static class NoOldCallRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);

        public NoOldCallRule() { this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            return new TestEntity(); // 即使返回也期望不被调用
        }

        @Override
        public void init() {
            this.addRule(e -> e.getName() != null, TestBrokenRuleMessage.NAME_ERROR);
        }
    }

    /** TC02: 多条规则调 getOldEntity */
    static class MultiOldCallRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        private final String oldValue;

        MultiOldCallRule(String oldValue) { this.oldValue = oldValue; this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            TestEntity old = new TestEntity();
            old.setName(oldValue);
            return old;
        }

        @Override
        public void init() {
            // 规则1：使用旧状态
            this.addRule(e -> {
                TestEntity old = this.getOldEntity();
                return old == null || e.getName().equals(old.getName());
            }, TestBrokenRuleMessage.NAME_ERROR);

            // 规则2：也使用旧状态（应走缓存）
            this.addRule(e -> {
                TestEntity old = this.getOldEntity();
                return old == null || e.getCode() != null;
            }, TestBrokenRuleMessage.CODE_ERROR);
        }
    }

    /** TC03: 验证缓存同一实例 */
    static class CacheCheckRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        boolean sameCacheInstance = false;
        private final String oldValue;

        CacheCheckRule(String oldValue) { this.oldValue = oldValue; this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            TestEntity old = new TestEntity();
            old.setName(oldValue);
            return old;
        }

        @Override
        public void init() {
            this.addRule(e -> {
                TestEntity first = this.getOldEntity();
                TestEntity second = this.getOldEntity();
                sameCacheInstance = (first == second);
                return true;
            }, TestBrokenRuleMessage.NAME_ERROR);
        }
    }

    /** TC04: 创建场景返回 null */
    static class NullOldEntityRule extends EntityRule<TestEntity> {
        boolean oldEntityWasNull = false;

        NullOldEntityRule() { this.init(); }

        @Override
        protected TestEntity supplyOldEntity() { return null; }

        @Override
        public void init() {
            this.addRule(e -> {
                oldEntityWasNull = (this.getOldEntity() == null);
                return true;
            }, TestBrokenRuleMessage.NAME_ERROR);
        }
    }

    /** TC05: 条件跳过时不触发 */
    static class InactiveConditionRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        private final String oldValue;

        InactiveConditionRule(String oldValue) { this.oldValue = oldValue; this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            return null;
        }

        @Override
        public void init() {
            this.addRule(e -> true, TestBrokenRuleMessage.NAME_ERROR,
                    model -> model.getStatus() == 1
                            ? ActiveStatus.ACTIVE
                            : ActiveStatus.INACTIVE);
        }
    }

    /** TC06: 混合规则 */
    static class MixedRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        private final String oldValue;

        MixedRule(String oldValue) { this.oldValue = oldValue; this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            TestEntity old = new TestEntity();
            old.setName(oldValue);
            return old;
        }

        @Override
        public void init() {
            // 规则1：不需要旧状态
            this.addRule(e -> e.getName() != null, TestBrokenRuleMessage.NAME_ERROR);
            // 规则2：需要旧状态
            this.addRule(e -> {
                TestEntity old = this.getOldEntity();
                return old == null || e.getName().equals(old.getName());
            }, TestBrokenRuleMessage.BOTH_ERROR);
        }
    }

    /** TC07: failFast */
    static class FailFastRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        private final String oldValue;

        FailFastRule(String oldValue) {
            super(true); // failFast
            this.oldValue = oldValue;
            this.init();
        }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            TestEntity old = new TestEntity();
            old.setName(oldValue);
            return old;
        }

        @Override
        public void init() {
            // 第一条规则就会失败
            this.addRule(e -> false, TestBrokenRuleMessage.NAME_ERROR);
            // 第二条规则不应执行（failFast）
            this.addRule(e -> {
                TestEntity old = this.getOldEntity();
                return old != null;
            }, TestBrokenRuleMessage.CODE_ERROR);
        }
    }

    /** TC08: 多次校验缓存独立 */
    static class MultiRunRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        private final String oldValue;
        int satisfyCount = 0;

        MultiRunRule(String oldValue) { this.oldValue = oldValue; this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            TestEntity old = new TestEntity();
            old.setName(oldValue + "_" + satisfyCount);
            return old;
        }

        @Override
        public void init() {
            this.addRule(e -> {
                satisfyCount++;
                TestEntity old = this.getOldEntity();
                return old != null;
            }, TestBrokenRuleMessage.NAME_ERROR);
        }
    }

    /** TC09: 参数化规则 + getOldEntity */
    static class ParamRuleWithOldRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        private final String oldValue;

        ParamRuleWithOldRule(String oldValue) { this.oldValue = oldValue; this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            TestEntity old = new TestEntity();
            old.setName(oldValue);
            return old;
        }

        @Override
        public void init() {
            this.addParamRule(e -> {
                TestEntity old = this.getOldEntity();
                if (old != null && !e.getName().equals(old.getName())) {
                    return RuleCheckResult.fail(new Object[]{old.getName(), e.getName()});
                }
                return RuleCheckResult.pass();
            }, TestBrokenRuleMessage.BOTH_ERROR);
        }
    }

    /** TC10: reset 兼容 */
    static class ResetTestRule extends EntityRule<TestEntity> {
        final AtomicInteger supplyInvokeCount = new AtomicInteger(0);
        private final String oldValue;

        ResetTestRule(String oldValue) { this.oldValue = oldValue; this.init(); }

        @Override
        protected TestEntity supplyOldEntity() {
            supplyInvokeCount.incrementAndGet();
            TestEntity old = new TestEntity();
            old.setName(oldValue);
            return old;
        }

        @Override
        public void init() {
            this.addRule(e -> {
                this.getOldEntity();
                return true;
            }, TestBrokenRuleMessage.NAME_ERROR);
        }
    }
}
