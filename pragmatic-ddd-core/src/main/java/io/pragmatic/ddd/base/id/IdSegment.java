package io.pragmatic.ddd.base.id;

/**
 * 一段连续可用的标识区间 [current, max]，左闭右闭。
 * current 为下一个将分配的 ID；max 为本段上限。
 */
public record IdSegment(long current, long max, long step) {

    /** 是否还有剩余可分配 ID。 */
    public boolean hasNext() {
        return current <= max;
    }

    /** 取出下一个 ID，并将 current 自增（返回新的不可变号段）。 */
    public IdSegment take() {
        if (!hasNext()) {
            throw new IllegalStateException("号段已耗尽，需先申请新号段");
        }
        return new IdSegment(current + 1, max, step);
    }

    public long remaining() {
        return Math.max(0, max - current + 1);
    }
}
