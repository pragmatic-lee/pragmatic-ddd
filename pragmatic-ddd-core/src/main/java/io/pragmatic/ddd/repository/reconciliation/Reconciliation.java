package io.pragmatic.ddd.repository.reconciliation;

/**
 * 对账结果（不可变值对象，目标无关）。
 * 用 record：天然不可变，自动生成组件访问器 status() / readVersion() / writeVersion()
 * 与基于值的 equals / hashCode，无需手写样板。
 *
 * @author wizard-lee
 */
public record Reconciliation(
        ReconciliationStatus status,  // 判定状态
        long readVersion,             // 异构存储副本版本 V'
        long writeVersion             // 写模型当前版本 V
) {
    /** 纯函数判定：先 UNTRACKED，再 ORPHAN（存在性），最后 CONSISTENT/STALE。 */
    public static Reconciliation of(long readVersion, long writeVersion) {
        ReconciliationStatus status;
        if (readVersion < 0) {
            status = ReconciliationStatus.UNTRACKED;
        } else if (writeVersion < 0) {
            status = ReconciliationStatus.ORPHAN;
        } else {
            status = readVersion >= writeVersion
                    ? ReconciliationStatus.CONSISTENT
                    : ReconciliationStatus.STALE;
        }
        return new Reconciliation(status, readVersion, writeVersion);
    }

    public boolean isStale()      { return status == ReconciliationStatus.STALE; }
    public boolean isConsistent() { return status == ReconciliationStatus.CONSISTENT; }
    public boolean isOrphan()     { return status == ReconciliationStatus.ORPHAN; }
    public boolean isUntracked()  { return status == ReconciliationStatus.UNTRACKED; }
}
