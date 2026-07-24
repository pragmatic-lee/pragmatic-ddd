package io.pragmatic.ddd.repository;

import java.util.List;

public interface IIDByReadDAORepository<R, ID> {
    R queryById(ID id, String returnClassName);


    /**
     * 根据多个订单信息查询订单列表
     */
    default List<R> queryByIdList(List<ID> idList,
                                  String returnClassName) {
        return null;
    }
}
