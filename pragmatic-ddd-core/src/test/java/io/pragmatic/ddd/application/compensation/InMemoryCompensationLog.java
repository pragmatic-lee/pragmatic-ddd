package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用内存补偿日志：记录调用轨迹（journal），支持原子认领与租约回收语义，供 L3 中继测试复用。
 *
 * @author wizard-lee
 */
public final class InMemoryCompensationLog implements ICompensationLog {

    private final Map<String, CompensationRecord> records = new LinkedHashMap<>();
    private final Map<String, Instant> claimedAt = new HashMap<>();
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
        if (current == null || !claimable(current.status())) {
            return false;
        }
        claimedAt.put(actionKey, Instant.now());
        replaceStatus(actionKey, CompensationStatus.COMPENSATING, 0);
        return true;
    }

    @Override
    public synchronized void markCompensated(String actionKey) {
        journal.add("markCompensated:" + actionKey);
        claimedAt.remove(actionKey);
        replaceStatus(actionKey, CompensationStatus.COMPENSATED, 0);
    }

    @Override
    public synchronized void markFailed(String actionKey, String reason) {
        journal.add("markFailed:" + actionKey);
        claimedAt.remove(actionKey);
        replaceStatus(actionKey, CompensationStatus.FAILED, 1);
    }

    @Override
    public synchronized void markConfirmed(String actionKey) {
        journal.add("markConfirmed:" + actionKey);
        CompensationRecord current = records.get(actionKey);
        if (current == null || !confirmable(current.status())) {
            return;
        }
        claimedAt.remove(actionKey);
        replaceStatus(actionKey, CompensationStatus.CONFIRMED, 0);
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

    @Override
    public synchronized List<CompensationRecord> findRetryableFailed(int limit, int maxAttempts) {
        return records.values().stream()
                .filter(record -> record.status() == CompensationStatus.FAILED)
                .filter(record -> record.attempts() < maxAttempts)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized int releaseStaleClaims(Duration lease) {
        Instant deadline = Instant.now().minus(lease);
        List<String> stale = claimedAt.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(deadline))
                .map(Map.Entry::getKey)
                .toList();
        for (String actionKey : stale) {
            journal.add("releaseStaleClaim:" + actionKey);
            claimedAt.remove(actionKey);
            replaceStatus(actionKey, CompensationStatus.EXECUTED, 0);
        }
        return stale.size();
    }

    /** 读取记录（测试断言用）。 */
    public synchronized CompensationRecord get(String actionKey) {
        return records.get(actionKey);
    }

    /** 预置记录（测试造数用）。 */
    public synchronized void seed(CompensationRecord record) {
        records.put(record.actionKey(), record);
    }

    /** 把某条记录的认领时间回拨指定时长（测试造数用，用于覆盖超时回收）。 */
    public synchronized void backdateClaim(String actionKey, Duration age) {
        Instant claimed = this.claimedAt.get(actionKey);
        if (claimed != null) {
            this.claimedAt.put(actionKey, claimed.minus(age));
        }
    }

    private boolean claimable(CompensationStatus status) {
        return status == CompensationStatus.EXECUTED || status == CompensationStatus.FAILED;
    }

    private boolean confirmable(CompensationStatus status) {
        return status == CompensationStatus.EXECUTED || status == CompensationStatus.COMPENSATING;
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
