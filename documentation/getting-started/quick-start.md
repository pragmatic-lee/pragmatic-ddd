# 快速开始

> 5 分钟跑通你的第一个 Pragmatic DDD 聚合根。

## 1. 引入依赖

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.pragmatic.ddd</groupId>
            <artifactId>pragmatic-ddd-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.pragmatic.ddd</groupId>
        <artifactId>pragmatic-ddd-core</artifactId>
    </dependency>
</dependencies>
```

## 2. 定义聚合根

```java
import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.OperationRegistry;

public class Order extends AggregateRoot<Long> {

    private String customerName;
    private long amount;
    private String status;

    public Order(Long id, String customerName, long amount) {
        this.setEntityId(id);
        this.customerName = customerName;
        this.amount = amount;
        this.status = "CREATED";
        this.markNew();       // 标记为新建，仓储据此走 insert
        this.markCreated();   // 填充审计时间
    }

    // 业务方法
    public void cancel() {
        this.status = "CANCELLED";
        this.markModified();
        this.getNewVersion();  // 触发版本递增
        this.recordOperation(OrderOperationRegistry.CANCEL);
        this.collectEvent(new OrderCancelledEvent(this.getEntityId()));
    }

    // --- 框架要求的两个抽象方法 ---

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return OrderOperationRegistry.INSTANCE;
    }

    // getter 省略
    public long getAmount() { return amount; }
    public String getStatus() { return status; }
}
```

## 3. 定义规则

```java
// 3.1 消息码注册表（注意：必须是 public）
public class OrderRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode AMOUNT_POSITIVE =
            MessageCode.of("ORDER_AMOUNT_POSITIVE", "订单金额必须大于0");

    public static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();
}

// 3.2 规则容器
public class OrderRule extends EntityRule<Order> {

    public OrderRule() {
        super(true); // failFast=true
        init();
    }

    @Override
    protected void init() {
        addRule((order, old) ->
                        order.getAmount() > 0 ? RuleCheckResult.pass()
                                              : RuleCheckResult.fail(),
                OrderRuleRegistry.AMOUNT_POSITIVE);
    }
}
```

## 4. 定义领域事件

```java
import io.pragmatic.ddd.event.BaseDomainEvent;

public class OrderCancelledEvent extends BaseDomainEvent {

    public OrderCancelledEvent(String entityId) {
        super(entityId);  // 自动生成 eventId + 时间戳
    }
}
```

## 5. 定义仓储

```java
public class OrderRepository implements IRepository<Long, Order> {

    @Override
    public void insert(Order order) { /* INSERT ... */ }

    @Override
    public void update(Order order) { /* UPDATE ... WHERE version = oldVersion */ }

    @Override
    public Order findById(Long id) { /* SELECT ... */ return null; }

    @Override
    public void remove(Order order) { /* DELETE ... */ }
}
```

## 6. 执行命令

```java
// 1. 构建聚合根
Order order = new Order(1L, "张三", 100);

// 2. 构建命令执行器（需要 IEventManager，这里用本地实现示意）
IEventManager eventManager = new ThreadPoolEventManager(LocalEventManagerConfig.defaultConfig());
eventManager.start();

CommandExecutor executor = new CommandExecutor(eventManager);
OrderRepository repository = new OrderRepository();
OrderRule rule = new OrderRule();

// 3. 执行：领域逻辑 → 规则校验 → 落库 → 发布事件 → 清空
Order result = executor.execute(order, rule, repository, Order::cancel);

// 4. 关闭
eventManager.shutdown();
```

## 7. 运行结果

执行流程按固定模板进行：

```
1. 领域逻辑    → order.cancel()：修改状态、记录操作、收集事件
2. 规则校验    → order.satisfiesRule(rule)：校验金额 > 0
3. 落库        → repository.save(order)：因 isNew=true 走 insert
4. 发布事件    → eventManager.publish(OrderCancelledEvent)
5. 清空状态    → order.clearWorkUnitState()
```

::: tip 试跑（Dry-run）
如果只想校验不落库，用 `executor.tryExecute(order, rule, repository, Order::cancel)`，返回 `DryRunResult`，零副作用。
:::

---

下一步：

- [领域建模](../core/domain-modeling.md)：深入了解实体、值对象、聚合根
- [业务规则引擎](../core/business-rules.md)：规则校验的完整能力
- [应用服务](../core/application-service.md)：命令执行器与工作单元
