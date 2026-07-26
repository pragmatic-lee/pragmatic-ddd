package io.pragmatic.ddd.repository.query;

/**
 * 分页请求（不可变值对象）。页码从 1 开始；页大小限定在 [1, 200]。
 */
public final class PageRequest {

    private final int pageNumber; // 1-based
    private final int pageSize;

    private PageRequest(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public static PageRequest of(int pageNumber, int pageSize) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1, but was " + pageNumber);
        }
        if (pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("pageSize must be in [1, 200], but was " + pageSize);
        }
        return new PageRequest(pageNumber, pageSize);
    }

    public int pageNumber() {
        return pageNumber;
    }

    public int pageSize() {
        return pageSize;
    }

    /** 偏移量，供 SQL 使用 */
    public int offset() {
        return (pageNumber - 1) * pageSize;
    }
}
