package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 分页结果（不可变值对象）。{@code data} 为防御性拷贝的不可变列表。
 *
 * @param <T> 投影类型
 *
 * @author wizard-lee
 */
public final class PageResult<T> {

    private final List<T> data;
    private final long totalCount;
    private final PageRequest request;

    private PageResult(List<T> data, long totalCount, PageRequest request) {
        this.data = List.copyOf(data);
        this.totalCount = totalCount;
        this.request = request;
    }

    public static <T> PageResult<T> of(List<T> data, long totalCount, PageRequest request) {
        return new PageResult<>(data, totalCount, request);
    }

    public List<T> data() {
        return data;
    }

    public long totalCount() {
        return totalCount;
    }

    public PageRequest request() {
        return request;
    }
}
