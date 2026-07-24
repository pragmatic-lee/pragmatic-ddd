package io.pragmatic.ddd.repository;

public interface IListByReadDAORepository<R,ListQuery>{
    R queryOneBy(ListQuery query, String returnClassName);
}
