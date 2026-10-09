package io.pragmatic.ddd.application.compensation;

import java.util.List;
import java.util.Optional;

/**
 * 测试用补偿动作：把正向 / 回写 / 逆向调用写入共享 journal，可配置抛错与重试策略。
 * 幂等键不再由动作自持，改由框架按 {聚合类型}:{标识}:{需求编码} 生成。
 *
 * @author wizard-lee
 */
public class RecordingAction implements ICompensableAction<TestAggregate, TestRequirement, String> {

    private final TestRequirement requirement;
    protected final List<String> journal;
    private boolean failOnExecute;
    private boolean failOnCompensate;
    private boolean policyDeclared = true;
    private CompensationPolicy policy = CompensationPolicy.defaultPolicy();

    public RecordingAction(String code, List<String> journal) {
        this.requirement = new TestRequirement(code);
        this.journal = journal;
    }

    public RecordingAction failOnExecute() {
        this.failOnExecute = true;
        return this;
    }

    public RecordingAction failOnCompensate() {
        this.failOnCompensate = true;
        return this;
    }

    public RecordingAction withPolicy(CompensationPolicy policy) {
        this.policy = policy;
        return this;
    }

    /** 不声明策略：policy() 返回 empty，用于验证回落范围默认策略。 */
    public RecordingAction withoutPolicy() {
        this.policyDeclared = false;
        return this;
    }

    /** 本动作对应的需求（构造补偿命令用）。 */
    public TestRequirement requirement() {
        return requirement;
    }

    /** 以默认测试聚合构造本动作的补偿命令（测试便捷方法）。 */
    public CompensationCommand<TestAggregate, TestRequirement, String> command() {
        return new CompensationCommand<>(this, new TestAggregate(), this.requirement);
    }

    @Override
    public Class<TestRequirement> requirementType() {
        return TestRequirement.class;
    }

    @Override
    public Class<TestAggregate> aggregateType() {
        return TestAggregate.class;
    }

    @Override
    public String execute(TestAggregate aggregateRoot, TestRequirement requirement) {
        if (failOnExecute) {
            throw new IllegalStateException("execute failed: " + requirement.code());
        }
        journal.add("execute:" + requirement.code());
        return "result-" + requirement.code();
    }

    @Override
    public void apply(TestAggregate aggregateRoot, String result) {
        journal.add("apply:" + requirement.code());
    }

    @Override
    public void compensate(TestAggregate aggregateRoot, String result) {
        journal.add("compensate:" + requirement.code());
        if (failOnCompensate) {
            throw new IllegalStateException("compensate failed: " + requirement.code());
        }
    }

    @Override
    public String payload(TestAggregate aggregateRoot, TestRequirement requirement) {
        return requirement.code();
    }

    @Override
    public Optional<CompensationPolicy> policy() {
        return this.policyDeclared ? Optional.of(policy) : Optional.empty();
    }
}
