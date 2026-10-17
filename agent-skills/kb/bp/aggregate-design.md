# BP 卡：聚合设计原则（aggregate-design）

> 源：`documentation/best-practices/aggregate-design.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 命名与包结构

```text
domain/{agg}/
├── model/
│   ├── {Agg}.java              # 聚合根 extends AggregateRoot<ID>
│   ├── {SubEntity}.java        # 子实体 extends AbstractEntity<ID>
│   └── {ValueObject}.java
├── operation/{Agg}OperationRegistry.java
├── rule/{Agg}Rule.java + {Agg}RuleConfig(装配) + {Agg}BrokenRuleRegistry.java
└── repository/Abstract{Agg}Repository.java
```

- 聚合根：`{Agg}`；子实体/值对象同目录 `model/`。
- 操作注册表 `operation/`、规则 `rule/`、仓储 `repository/` 与 `model/` 平级。

## 2. 父类已托管能力（不要在聚合根里重造）

继承 `AggregateRoot<T>` 后，身份标识、审计、软删、版本号、规则校验、领域事件、操作追踪**全部由父类托管**：

| 托管字段 | 说明 |
| --- | --- |
| `entityId` | 经 `setEntityId(T)` 赋值；重建由仓储回填 |
| `createdAt`/`updatedAt`/`createdBy`/`updatedBy` | 审计；只经 `markCreated()`/`markModified()` 写入 |
| `oldVersion` | 上次持久化版本，仓储 `findById` 回填 |
| `isNew` | 新建标记，仓储 `save()` 据此路由 insert/update |

- **等同性**：`equals`/`hashCode` 由 `AbstractEntity` 基于 `entityId`，**不要覆盖**。
- **事件收集（protected，仅聚合根内部）**：`collectEvent(BaseDomainEvent)` / `collectEvent(BaseDomainEvent, EntityOperation)` / `collectEvent(Supplier<IDomainEvent>)`（延迟）/ `getDomainEvents()` / `triggerDataSyncHook()`。用无操作参数的 `collectEvent` 前必须先 `recordOperation`，否则启用了操作体系时抛 `OperationException`。
- **两个抽象方法必须实现**：`brokenRuleRegistry()`（**不能返回 null**）、`operationRegistry()`（返回 null = 不启用操作体系）。

## 3. 聚合根骨架与约定

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(AccessLevel.PROTECTED) // 取代手写 protected {Agg}(){}，仅供持久化重建
public class Order extends AggregateRoot<Long> {

    private String name;
    private int age;

    // 业务构造（public 全参）：唯一承载初始化逻辑
    public Order(OrderInitData data) {
        this.name = data.getName();
        this.age = data.getAge();
        this.recordOperation(OrderOperationRegistry.CREATE);
        this.markCreated();  // 审计时间戳：createdAt/updatedAt = now（继承自 AbstractEntity）
        this.markNew();      // 持久化路由标志：isNew=true，仓储 save 据此走 INSERT（与 markCreated 职责不同，两者都要调）
        this.collectEvent(() -> OrderCreatedEvent.buildEvent(this)); // 延迟事件（构造期 ID 可能未定）
    }

    // 充血方法：纯赋值，无 if+throw 守卫、无数据组装/转换
    public void update(OrderUpdateData data) {
        this.name = data.getName();
        this.age = data.getAge();
        this.markModified();
        this.recordOperation(OrderOperationRegistry.UPDATE);
        this.collectEvent(OrderUpdatedEvent.buildEvent(this));
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderBrokenRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return OrderOperationRegistry.INSTANCE;
    }
}
```

### 3.1 充血方法两条铁律（硬约束 4）
- **不做规则校验**：`if + throw` 守卫不放业务方法内；校验由 `satisfiesRule`/`EntityRule` 在合适时机触发。
- **不做数据组装与转换**：值对象、已转换字段由调用方/工厂（`EntityFactory`/`EntityUpdater`）组装好再传入；业务方法只做字段赋值与状态变更。

### 3.2 参数对象 `IParamObject`（入参 >5 个或常成组出现时）
```java
@Data // 纯数据容器：加注解即可，无需手写构造；不继承领域基类、无业务逻辑
public class OrderInitData implements IParamObject {
    private String name;
    private int age;
}
```
与值对象（`IValueObject`）区别：参数对象是入参载体、不持久化、等同性由 Lombok 生成；值对象是领域内可嵌入结构，判等是领域行为。

### 3.3 Lombok 约定（硬约束 8）
聚合根：`@Getter` + `@Setter(AccessLevel.PROTECTED)` + `@NoArgsConstructor(AccessLevel.PROTECTED)`；**禁用 `@Data`/`@EqualsAndHashCode`/`@Builder`**（等同性由父类托管）。值对象见 [值对象最佳实践](./value-object.md)。

## 4. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 大聚合根 | 拆小聚合，ID 引用 |
| 跨聚合持对象引用 | 只持 ID（硬约束 13） |
| 聚合间直接调用 | 经领域事件解耦 |
| 业务方法内 `if+throw` 守卫 | 上交规则容器（硬约束 4） |
| 业务方法内做数据组装/转换 | 集中在 Factory/Updater/调用方 |
| 聚合根用 `@Data`/`@Builder` | `@Getter`+`@Setter(PROTECTED)`+`@NoArgsConstructor(PROTECTED)` |
| 自定义 ID/版本/审计字段 | 用父类托管能力（硬约束 14） |
| 覆盖 `equals`/`hashCode` | 由 `AbstractEntity` 基于 entityId |

## 5. 被读于

S4 `ddd-aggregate-create`（步骤 2~4）、S7 `ddd-code-review`。
