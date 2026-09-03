package io.pragmatic.ddd.example.order.api.common;

import lombok.Getter;

import java.util.List;

/**
 * 分页响应结构（对齐 api-contract.md 1.2）：list/total/pageNo/pageSize，pageNo 从 1 起。
 *
 * @param <T> 列表行类型
 * @author wizard-lee
 */
@Getter
public final class PageResultDTO<T> {

    private final List<T> list;

    private final long total;

    private final int pageNo;

    private final int pageSize;

    private PageResultDTO(List<T> list, long total, int pageNo, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    /** 组装分页响应。 */
    public static <T> PageResultDTO<T> of(List<T> list, long total, int pageNo, int pageSize) {
        return new PageResultDTO<>(list, total, pageNo, pageSize);
    }
}
