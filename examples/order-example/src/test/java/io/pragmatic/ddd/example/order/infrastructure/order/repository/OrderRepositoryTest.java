package io.pragmatic.ddd.example.order.infrastructure.order.repository;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.param.OrderInitData;
import io.pragmatic.ddd.example.order.infrastructure.order.config.MySqlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderRepository 集成式单元测试。
 *
 * <p>连接本地 MySQL（order_example 库），真实执行 MyBatis 映射，验证主表 CRUD、
 * JSON 值对象序列化往返、枚举 CODE 持久化、TrackedList 懒加载与结构差量同步、乐观锁 CAS。</p>
 *
 * <p>运行方式：仅装配 MySQL 链路（MySqlConfig + OrderRepository），不加载 ES/Redis/RocketMQ；
 * 每个用例事务真实提交（@Rollback(false)），@AfterEach 后置删除本次写入数据，便于测试后查看落库结果。</p>
 *
 * @author wizard-lee
 */
@SpringBootTest(classes = OrderRepositoryTest.TestConfig.class)
class OrderRepositoryTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({MySqlConfig.class, OrderRepository.class})
    static class TestConfig {
        // 仅装配 MyBatis + 数据源 + 事务管理器 + OrderRepository，不加载 ES/Redis/RocketMQ
    }

    @Autowired
    private OrderRepository repo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 已写入的订单 id，@AfterEach 统一后置删除（先删子表再删主表）。 */
    private final List<Long> createdOrderIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long orderId : createdOrderIds) {
            jdbcTemplate.update("DELETE FROM t_order_item WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM t_order WHERE order_id = ?", orderId);
        }
        createdOrderIds.clear();
    }

    // ==================== 4.1 insert ====================

    /** 验证 insert：订单主表 + 全部订单项应落库，JSON 值对象/枚举往返正确。 */
    @Test
    void insert_shouldPersistOrderAndItems() {
        Long id = 1001L;
        repo.insert(newOrder(id));
        createdOrderIds.add(id);

        Order loaded = repo.findById(id);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(loaded.getCustomer().getCustomerName()).isEqualTo("张三");
        assertThat(loaded.getTotalAmount().getAmount()).isEqualByComparingTo("9787");
        assertThat(loaded.getOrderItems().getAllItems()).hasSize(2);
    }

    // ==================== 4.2 findById ====================

    /** 验证 findById：返回完整聚合，且事务内访问 orderItems 可触发 MyBatis 懒加载子查询。 */
    @Test
    void findById_shouldReturnLazyOrderItems() {
        Long id = 1002L;
        repo.insert(newOrder(id));
        createdOrderIds.add(id);

        Order loaded = repo.findById(id);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getEntityId()).isEqualTo(id);
        // 事务内访问触发懒加载子查询
        assertThat(loaded.getOrderItems().getAllItems()).hasSize(2);
    }

    /** 验证 findById：不存在的订单 id 返回 null。 */
    @Test
    void findById_shouldReturnNullWhenNotExist() {
        assertThat(repo.findById(999999L)).isNull();
    }

    // ==================== 4.3 existsById ====================

    /** 验证 existsById：已存在的订单返回 true，不存在的返回 false。 */
    @Test
    void existsById_shouldReturnTrueWhenExist() {
        Long id = 1003L;
        repo.insert(newOrder(id));
        createdOrderIds.add(id);

        assertThat(repo.existsById(id)).isTrue();
        assertThat(repo.existsById(999999L)).isFalse();
    }

    // ==================== 4.4 currentVersion ====================

    /** 验证 currentVersion：返回数据库中的当前乐观锁版本号（新单落库后为 2）。 */
    @Test
    void currentVersion_shouldReturnDbVersion() {
        Long id = 1004L;
        repo.insert(newOrder(id));
        createdOrderIds.add(id);

        assertThat(repo.currentVersion(id)).isEqualTo(2); // 新单落库后 version=2
    }

    // ==================== 4.5 update ====================

    /** 验证 update：以 CAS 条件命中后更新订单字段，并递增乐观锁版本号。 */
    @Test
    void update_shouldUpdateOrderFields() {
        Long id = 1005L;
        Order order = newOrder(id);
        repo.insert(order);
        createdOrderIds.add(id);
        Order loaded = repo.findById(id);

        loaded.changeAddress(new Address("上海市", "上海市", "浦东新区", "世纪大道100号", "李四", "13900000000"));
        repo.update(loaded);

        Order loaded2 = repo.findById(id);
        assertThat(loaded2.getShippingAddress().getCity()).isEqualTo("上海市");
        assertThat(loaded2.getOldVersion()).isEqualTo(3);
    }

    // ==================== 4.6 乐观锁冲突 ====================

    /** 验证乐观锁：并发改写 DB 版本使内存版本过期后执行 update，应抛出乐观锁冲突异常。 */
    @Test
    void update_withStaleVersion_shouldThrow() {
        Long id = 1006L;
        repo.insert(newOrder(id));
        createdOrderIds.add(id);
        Order stale = repo.findById(id);
        stale.changeAddress(new Address("上海市", "上海市", "浦东新区", "世纪大道100号", "李四", "13900000000"));
        // 模拟并发：直接改 DB version，使内存中 oldVersion 过期
        jdbcTemplate.update("UPDATE t_order SET version = 99 WHERE order_id = ?", id);


        assertThatThrownBy(() -> repo.update(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    // ==================== 4.7 TrackedList 差量同步 ====================

    /** 验证 TrackedList 差量同步：update 后新增项落库、被删基线项物理删除，子表数据与内存集合一致。 */
    @Test
    void update_shouldSyncTrackedListDiff() {
        Long id = 1007L;
        Order order = newOrder(id);
        repo.insert(order); // 落库 2 个订单项（9001、9002）
        createdOrderIds.add(id);

        // 新增 1 项
        order.addItem(newOrderItem(9003L, "MacBook", "M3", 9999, 1),
                new Money(BigDecimal.valueOf(19786), "CNY"));
        // 删除第 2 项（基线项）
        order.removeItem(9002L, new Money(BigDecimal.valueOf(9787), "CNY"));
        repo.update(order);

        Order loaded = repo.findById(id);
        assertThat(loaded.getOrderItems().getAllItems()).hasSize(2); // 9001 + 9003

        Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_order_item WHERE order_id = ?", Integer.class, id);
        assertThat(dbCount).isEqualTo(2);
    }

    // ==================== 4.8 remove ====================

    /** 验证 remove：删除订单主表的同时级联清理子表订单项，不留孤儿行。 */
    @Test
    void remove_shouldDeleteOrderAndItems() {
        Long id = 1008L;
        repo.insert(newOrder(id));
        createdOrderIds.add(id);

        Order loaded = repo.findById(id);
        repo.remove(loaded);

        assertThat(repo.existsById(id)).isFalse();
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_order_item WHERE order_id = ?", Integer.class, id);
        assertThat(itemCount).isZero();
    }

    // ==================== 测试数据构建 ====================

    private Order newOrder(Long orderId) {
        OrderInitData data = new OrderInitData();
        data.setCustomer(new Customer(1001L, "张三"));
        data.setShippingAddress(new Address("广东省", "深圳市", "南山区", "科技园路1号", "张三", "13800000000"));
        data.setRemark("测试订单");
        data.setPaymentMethod(PaymentMethod.WECHAT);
        data.setTotalAmount(new Money(BigDecimal.valueOf(9787), "CNY"));
        data.setOrderItems(List.of(
                newOrderItem(9001L, "iPhone 15", "256G", 5999, 1),
                newOrderItem(9002L, "AirPods Pro", "标准", 1899, 2)
        ));
        return new Order(data, orderId);
    }

    private OrderItem newOrderItem(Long productId, String name, String spec, int price, int qty) {
        return new OrderItem(productId, name, spec, new Money(BigDecimal.valueOf(price), "CNY"), qty);
    }
}
