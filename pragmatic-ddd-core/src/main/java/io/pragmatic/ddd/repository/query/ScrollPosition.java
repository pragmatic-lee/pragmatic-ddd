package io.pragmatic.ddd.repository.query;

/**
 * 滚动游标（不可变值对象）。游标为不透明字符串，由实现层编解码。
 */
public final class ScrollPosition {

    private static final ScrollPosition INITIAL = new ScrollPosition(null);

    private final String cursor;

    private ScrollPosition(String cursor) {
        this.cursor = cursor;
    }

    /** 由实现层返回的游标字符串构造 */
    public static ScrollPosition of(String cursor) {
        return new ScrollPosition(cursor);
    }

    /** 首次查询使用的初始游标 */
    public static ScrollPosition initial() {
        return INITIAL;
    }

    /** 游标字符串；{@code null} 表示初始位置 */
    public String cursor() {
        return cursor;
    }

    public boolean isInitial() {
        return cursor == null;
    }
}
