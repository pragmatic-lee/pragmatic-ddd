package io.pragmatic.ddd.repository;

import java.util.List;

public interface IOneByReadDAORepository <R,ListQuery>{
    List<R> queryListBy(ListQuery query, String returnClassName);
}
