package io.pragmatic.ddd.repository;

public interface IReadDAORepository<ID, R, ListQuery, OneQuery, PageQuery, ScrollQuery> extends
        IOneByReadDAORepository<R, OneQuery>,
        IPageAndScrollByReadDAORepository<R, PageQuery, ScrollQuery>,
        IIDByReadDAORepository<R, ID>,
        IListByReadDAORepository<R, ListQuery> {


}
