package io.pragmatic.ddd.repository;

import java.util.List;

public interface IPageAndScrollByReadDAORepository<R, PageQuery, ScrollQuery> {

    PageInfoDAO<R> queryPageBy(PageQuery orderPageQueryDTO,
                               PageNumberDAO pageNumber,
                               String returnClassName);

    default List<R> queryScrollBy(PageQuery orderPageQueryDTO,
                                  ScrollQuery scrollQueryDTO, String returnClassName) {
        return null;

    }
}
