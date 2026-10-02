package io.pragmatic.ddd.application.compensation;

import java.util.List;

/**
 * 测试用补偿动作：把正向 / 逆向调用写入共享 journal，可配置抛错与重试策略。
 *
 * @author wizard-lee
 */
public class RecordingAction implements ICompensableAction<String> {

    private final String actionKey;
    protected final List<String> journal;
    private boolean failOnExecute;
    private boolean failOnCompensate;
    private CompensationPolicy policy = CompensationPolicy.defaultPolicy();

    public RecordingAction(String actionKey, List<String> journal) {
        this.actionKey = actionKey;
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

    @Override
    public String actionKey() {
        return actionKey;
    }

    @Override
    public String execute() {
        if (failOnExecute) {
            throw new IllegalStateException("execute failed: " + actionKey);
        }
        journal.add("execute:" + actionKey);
        return "result-" + actionKey;
    }

    @Override
    public void compensate(String result) {
        journal.add("compensate:" + actionKey);
        if (failOnCompensate) {
            throw new IllegalStateException("compensate failed: " + actionKey);
        }
    }

    @Override
    public CompensationPolicy policy() {
        return policy;
    }
}
