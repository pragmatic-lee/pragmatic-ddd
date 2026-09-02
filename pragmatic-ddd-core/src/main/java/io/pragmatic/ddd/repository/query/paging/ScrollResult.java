package io.pragmatic.ddd.repository.query.paging;

import java.util.List;

/**
 * 滚动结果（不可变值对象）。{@code nextCursor == null} 表示无更多数据。
 *
 * @param <T> 投影类型
 *
 * @author wizard-lee
 */
public final class ScrollResult<T> {

    private final List<T> data;
    private final String nextCursor;

    private ScrollResult(List<T> data, String nextCursor) {
        this.data = List.copyOf(data);
        this.nextCursor = nextCursor;
    }

    public static <T> ScrollResult<T> of(List<T> data, String nextCursor) {
        return new ScrollResult<>(data, nextCursor);
    }

    public List<T> data() {
        return data;
    }

    /** 下一页游标；{@code null} 表示已到末页 */
    public String nextCursor() {
        return nextCursor;
    }
}
