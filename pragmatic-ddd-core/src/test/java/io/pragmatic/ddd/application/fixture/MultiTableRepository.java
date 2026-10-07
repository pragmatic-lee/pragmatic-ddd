package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.repository.IRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令执行器测试专用仓储夹具：模拟"一个聚合映射多张表"的落库，一次 save 顺序发出多条表级语句，
 * 并按事务的提交/回滚结果决定这些语句是否真正落库，用于验证多表语句的原子性。
 */
public class MultiTableRepository implements IRepository<Long, DryRunAggregate> {

    /** 落库动作名称。 */
    public static final String SAVE = "save";

    private final RecordingTransactionOperations txOps;
    private final List<String> statements;
    private final int failAtIndex;
    private final List<String> staged = new ArrayList<>();
    private final List<String> committed = new ArrayList<>();
    private int saveCount = 0;

    /**
     * 创建多表仓储夹具。
     *
     * @param txOps        事务夹具，用于接收提交/回滚通知
     * @param statements   一次 save 发出的表级语句，按发出顺序排列
     * @param failAtIndex  失败语句的下标；小于 0 表示全部成功
     */
    public MultiTableRepository(RecordingTransactionOperations txOps, List<String> statements, int failAtIndex) {
        this.txOps = txOps;
        this.statements = List.copyOf(statements);
        this.failAtIndex = failAtIndex;
        txOps.onCompletion(committedFlag -> this.onTransactionCompleted(committedFlag));
    }

    private void onTransactionCompleted(boolean committedFlag) {
        if (committedFlag) {
            this.committed.addAll(this.staged);
        }
        this.staged.clear();
    }

    @Override
    public void insert(DryRunAggregate aggregateRoot) {
        this.emit();
    }

    @Override
    public void update(DryRunAggregate aggregateRoot) {
        this.emit();
    }

    private void emit() {
        this.saveCount++;
        // 记录 save 动作，用于验证落库发生在事务内、且早于事务提交
        this.txOps.record(SAVE);
        for (int i = 0; i < this.statements.size(); i++) {
            if (i == this.failAtIndex) {
                throw new IllegalStateException("SQL failed: " + this.statements.get(i));
            }
            this.staged.add(this.statements.get(i));
        }
    }

    @Override
    public DryRunAggregate findById(Long id) {
        return null;
    }

    @Override
    public void remove(DryRunAggregate aggregateRoot) {
        // 命令执行器测试不涉及删除
    }

    /** 返回累计的落库次数。 */
    public int saveCount() {
        return this.saveCount;
    }

    /** 返回事务提交后真正落库的表级语句。 */
    public List<String> committedStatements() {
        return List.copyOf(this.committed);
    }
}
