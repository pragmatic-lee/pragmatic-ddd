package io.pragmatic.ddd.example.order.infrastructure.order.repository;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.repository.AbstractOrderRepository;
import io.pragmatic.ddd.track.TrackedList;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 订单聚合仓储实现：经 SqlSessionTemplate 操作 OrderMapper（纯 XML），
 * 完成订单主表整存整取与 TrackedList<OrderItem> 的结构差量同步。
 *
 * <p>事务边界由调用方（应用层 @Transactional）负责；orderItems 为 MyBatis 懒加载，
 * 调用方在事务内首次访问时触发子查询。</p>
 *
 * @author wizard-lee
 */
@Repository
public class OrderRepository extends AbstractOrderRepository {

    private final SqlSessionTemplate sqlSessionTemplate;

    public OrderRepository(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    protected void doInsert(Order aggregateRoot) {
        sqlSessionTemplate.insert("OrderMapper.insert", aggregateRoot);
        List<OrderItem> allItems = aggregateRoot.getOrderItems().getAllItems();
        if (!allItems.isEmpty()) {
            batchInsertOrderItems(aggregateRoot.getEntityId(), allItems);
        }
    }

    @Override
    protected void doUpdate(Order aggregateRoot) {
        int affected = sqlSessionTemplate.update( "OrderMapper.update", aggregateRoot);
        if (affected == 0) {
            throw new OptimisticLockingFailureException(
                    "订单 [" + aggregateRoot.getEntityId() + "] 乐观锁冲突，期望版本 ["
                            + aggregateRoot.getOldVersion() + "]");
        }
        syncTrackedList(aggregateRoot);
    }

    @Override
    protected void doRemove(Order aggregateRoot) {
        sqlSessionTemplate.delete("OrderMapper.deleteOrderItemsByOrderId", aggregateRoot.getEntityId());
        sqlSessionTemplate.delete("OrderMapper.deleteById", aggregateRoot.getEntityId());
    }

    @Override
    public Order findById(Long aLong) {
        return sqlSessionTemplate.selectOne("OrderMapper.selectById", aLong);
    }

    @Override
    public boolean existsById(Long aLong) {
        return super.existsById(aLong);
    }

    @Override
    public long currentVersion(Long aLong) {
        return super.currentVersion(aLong);
    }

    /** 差量同步订单项：删除 removed 桶，插入 appended 桶。 */
    private void syncTrackedList(Order aggregateRoot) {
        TrackedList<OrderItem, Long> orderItems = aggregateRoot.getOrderItems();
        List<OrderItem> removedItems = orderItems.getRemovedItems();
        if (!removedItems.isEmpty()) {
            List<Long> removedIds = removedItems.stream().map(OrderItem::id).toList();
            sqlSessionTemplate.delete("OrderMapper.deleteOrderItemsByIds", removedIds);
        }
        List<OrderItem> appendedItems = orderItems.getAppendedItems();
        if (!appendedItems.isEmpty()) {
            this.batchInsertOrderItems(aggregateRoot.getEntityId(), appendedItems);
        }
    }

    private void batchInsertOrderItems(Long orderId, List<OrderItem> items) {
        Map<String, Object> param = Map.of("orderId", orderId, "items", items);
        sqlSessionTemplate.insert("OrderMapper.batchInsertOrderItems", param);
    }
}
