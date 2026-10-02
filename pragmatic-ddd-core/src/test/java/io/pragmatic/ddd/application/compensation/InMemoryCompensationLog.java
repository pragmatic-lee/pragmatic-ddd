package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用内存补偿日志：记录调用轨迹（journal），支持原子认领语义，供 L3 中继测试复用。
 *
 * @author wizard-lee
 */
public final class InMemoryCompensationLog implements ICompensationLog {

    private final Map<String, CompensationRecord> records = new LinkedHashMap<>();
    private final List<String> journal;

    public InMemoryCompensationLog() {
        this(new ArrayList<>());
    }

    public InMemoryCompensationLog(List<String> journal) {
        this.journal = journal;
    }

    @Override
    public synchronized void record(CompensationRecord record) {
        journal.add("record:" + record.actionKey() + ":" + record.status());
        records.put(record.actionKey(), record);
    }

    @Override
    public synchronized void markExecuted(String actionKey) {
        journal.add("markExecuted:" + actionKey);
        replaceStatus(actionKey, CompensationStatus.EXECUTED, 0);
    }

    @Override
    public synchronized boolean markCompensating(String actionKey, String claimToken) {
        journal.add("markCompensating:" + actionKey);
        CompensationRecord current = records.get(actionKey);
        if (current == null || current.status() != CompensationStatus.EXECUTED) {
            return false;
        }
        replaceStatus(actionKey, CompensationStatus.COMPENSATING, 0);
        return true;
    }

    @Override
    public synchronized void markCompensated(String actionKey) {
        journal.add("markCompensated:" + actionKey);
        replaceStatus(actionKey, CompensationStatus.COMPENSATED, 0);
    }

    @Override
    public synchronized void markFailed(String actionKey, String reason) {
        journal.add("markFailed:" + actionKey);
        replaceStatus(actionKey, CompensationStatus.FAILED, 1);
    }

    @Override
    public synchronized List<CompensationRecord> findExecuted(int limit) {
        return findByStatus(CompensationStatus.EXECUTED, limit);
    }

    @Override
    public synchronized List<CompensationRecord> findSuspended(int limit) {
        return findByStatus(CompensationStatus.PENDING, limit);
    }

    @Override
    public synchronized List<CompensationRecord> findFailed(int limit) {
        return findByStatus(CompensationStatus.FAILED, limit);
    }

    /** 读取记录（测试断言用）。 */
    public synchronized CompensationRecord get(String actionKey) {
        return records.get(actionKey);
    }

    /** 预置记录（测试造数用）。 */
    public synchronized void seed(CompensationRecord record) {
        records.put(record.actionKey(), record);
    }

    private List<CompensationRecord> findByStatus(CompensationStatus status, int limit) {
        return records.values().stream()
                .filter(record -> record.status() == status)
                .limit(limit)
                .toList();
    }

    private void replaceStatus(String actionKey, CompensationStatus status, int attemptsDelta) {
        CompensationRecord current = records.get(actionKey);
        if (current == null) {
            return;
        }
        records.put(actionKey, new CompensationRecord(
                current.actionKey(),
                status,
                current.handler(),
                current.payload(),
                current.attempts() + attemptsDelta,
                Instant.now()));
    }
}
