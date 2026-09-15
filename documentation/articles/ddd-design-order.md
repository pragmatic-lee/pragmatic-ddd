---
title: 落地 DDD 的真正第一步，不是画 ER 图
date: 2026-09-15
description: 落地 DDD 的第一步是设计业务模型，不是设计数据库。先识别聚合根与实体，再梳理规则、事件、事件订阅，最后才轮到 MySQL、ES 和 Redis——因为数据库只负责存数，数字的含义由模型来定义。
---

# 落地 DDD 的真正第一步，不是画 ER 图

> **摘要**：落地 DDD 的第一步是设计业务模型，不是设计数据库。先识别聚合根与实体，再梳理规则、事件、事件订阅，最后才轮到 MySQL、ES 和 Redis——因为数据库只负责存数，数字的含义由模型来定义。

---

## 开场白：需求评审结束后的第一反应

需求评审刚结束，大家回到工位上，接下来该做什么？

很多团队的第一反应是：**先设计数据库。**

打开 Navicat 建 `order`、`order_item`、`payment` 三张表，字段一列、外键一连，后端拿到表结构生成 PO，Service 层开始堆逻辑。这套动作行云流水，在传统开发里几乎无可挑剔——先定存储、再写代码，本来就是大多数人接受的训练。

问题恰恰在这儿——**这套动作跟你用不用 DDD 框架，没有半点关系**。

如果你上了 DDD 框架，聚合根、实体、值对象、领域事件一应俱全，但设计的起点仍然是一张 ER 图，那你只是把 DTO 换了个名字叫 Entity，把 DAO 换了个名字叫 Repository。术语是新的，思路还是老的。

为什么会这样？因为**数据库里存的东西本身是没有含义的**。表里躺着一个 `status = 5`，它只是数字 5；再冒出来一个 `6`，也只是数字 6。5 和 6 分别代表什么业务含义——是"已支付"还是"已发货"?——数据库说不出来，能解释它们的只有模型。

于是就有了一个根本分歧：**数据库只管存数，数的意义由模型来定义。** 那么在设计时，我们究竟该先定义"意义"，还是先定义"数"?答案不言自明。

这篇文章想讲清楚一件事：**真正落地 DDD，设计的第一步是设计业务模型，而不是设计存储。**

读完你能拿到三样东西：

- 一条完整的 DDD 设计顺序，从识别聚合根一路走到建表
- 一把"哪些东西必须先定、哪些东西必须后定"的尺子
- 一份"设计做没做到位"的自检清单，以及几道能立刻自查的判断题

---

## 先给结论：数据库在设计顺序里排最后

我先把顺序摆出来，后面再逐条展开。

1. 从需求里识别**聚合根**，找到**实体**，把实体定义出来
2. 梳理**业务模型**：规则、领域事件、事件订阅，把所有业务动作列全
3. 用 DDD 框架的构件，把这些业务动作表达出来
4. 业务模型定稿
5. 基于业务模型，设计 MySQL 表、ES 索引、Redis 结构

请盯着前四步看几秒钟：**一个技术名词都没有。**

没有 MySQL，没有 Elasticsearch，没有 Redis，没有事务传播级别，也没有分库分表。

这不是说存储不重要。而是说——**存储是被业务模型推导出来的结果，不是推导业务模型的起点。**

顺序反了，你得到的是"用 DDD 术语包装的三层架构"；顺序对了，框架才真正开始替你干活。

好了，道理讲完了。接下来咱们一步步拆。

---

## 从需求里把聚合根和实体拎出来

拿到需求先别想表，先想：**这个系统里有哪些核心的业务概念？**

概念找出来之后，用三个问题把它们归类：

1. **谁有独立的生命周期？** —— 它被创建、被查询、被作废，都不依附于别人。这是聚合根候选。
2. **谁离开了谁就没有意义？** —— 有意义的那个是根，没意义的是它内部的实体。
3. **哪些数据必须在同一时刻保持一致？** —— 这条线画出来的就是聚合的边界，也就是一致性边界。

拿订单系统举例：

- 订单（Order）能独立创建、独立查询，它是**聚合根**。
- 订单项（OrderItem）离开订单还有意义吗？没有。所以它是订单聚合内部的**实体**。
- 收货地址、金额这类东西，我们只关心它的值，不关心它是"哪一个"，所以是**值对象**，用 `Address`、`Money` 表达。

这里有个我自己摇摆过很久的点：**支付**到底算订单聚合的一部分，还是独立聚合？

关键在于分清两样东西：**"订单是否已支付"这个状态**，和**"一笔笔支付流水（Payment）"这个记录**。它俩长得像，边界却完全不同：

- **订单的支付状态**，是订单自身的一个属性。用户在我们的订单页看着它、发货规则依赖它、售后按它判断，它就是订单的状态快照之一。放在 `Order` 里顺理成章。
- **支付流水**，往往有自己的状态机（待支付 / 支付中 / 已支付 / 已退款）、有自己的对账查询入口，还会一次支付对应多笔流水。把它塞进订单，会让订单聚合越来越大，一次加载要拖出一堆流水。

我早年的做法是不分青红皂白，把流水也塞进订单聚合，理由是"它离开订单没意义"。后来踩了坑——订单聚合被撑得臃肿，每次 `findById` 都要连流水一起拖出来，而绝大多数场景根本用不上。

所以现在我区分对待：**订单只需要一个支付状态，就让它留在订单里；支付流水这类有独立生命周期、独立查询诉求的东西，才单独成聚合，用领域事件跟订单通信。**

这个判断标准我用得挺久，边界偶尔还是会模糊。遇到模糊的时候我倾向于先拆开——拆错了合并容易，合错了再拆就疼了。

到这一步为止，我们写下的东西大概长这样：

```
聚合根：Order
  属性：下单客户、订单状态、支付状态、物流状态、总金额、收货地址
  内部实体：OrderItem（商品 ID、商品名、规格、单价、数量）
  值对象：Money、Address、Customer、PaymentInfo
  规则：见下一步（独立规则容器）
  操作：下单、支付、发货、签收、取消、改址
```

纯文本，没有一行代码，也没有一张表。

---

## 把业务动作全梳理出来：规则、事件、事件订阅

这一步是整个设计过程里最值钱、也最容易被跳过的一步。

很多人识别完聚合根就急着去写实体类，结果写着写着发现逻辑没地方放，又退回到 Service 里。原因就是**业务动作没梳理，只梳理了数据结构**。

要梳理的业务动作有三类：

### 第一类：业务规则（不变量）

规则是这个聚合**在任何时刻都必须成立**的约束。它不是"某个流程里的 if"，而是"破坏它，这个对象就不合法了"。

订单的规则比如：

- 已支付的订单不能重复支付
- 已发货的订单不能修改收货地址
- 订单总额必须等于所有订单项金额之和

### 第二类：领域事件（已经发生的事实）

注意措辞：**事件是事实，不是动作**。所以它用过去式命名——`OrderPaidEvent` 而不是 `PayOrderEvent`。

一般情况下，**一个业务动作对应一个领域事件**：`pay()` 对应 `OrderPaidEvent`，`cancel()` 对应 `OrderCancelledEvent`，`ship()` 对应 `OrderShippedEvent`。一一对应是常态，因为事件回答的就是"这个动作做完了"这一件事。

什么时候会一对多？当这个动作的影响溢出了当前聚合的边界。用户支付成功，对订单而言只产生"订单已支付"这一个事实；如果付款时顺带锁了库存，那"库存已锁定"是库存聚合自己的事实，由库存自己去记，不该塞进 `OrderPaidEvent`。

反过来也别把多个事实硬压成一个——不要搞 `OrderPaidAndStockLockedEvent` 这种打包事件。事件一打包，订阅方就被迫关心自己根本不需要的那部分，解耦又没了。

所以判断标准就一句话：**盯住当前这个聚合，它身上发生了什么变化，就记哪个事实。** 别的变化，交给别的聚合自己的事件去记。

### 第三类：事件订阅（谁关心这个事实）

这是最容易被忽略的一类，也恰恰是 DDD 解耦的关键。

问一句：**这个事实发生后，还有谁需要知道？**

| 场景 | 触发动作 | 聚合状态变化 | 领域事件 | 订阅方与后续动作 |
| --- | --- | --- | --- | --- |
| 用户下单 | 创建订单 | 待支付 | `OrderCreatedEvent` | 库存服务锁库存；营销服务冻结优惠券 |
| 用户支付 | 支付 | 已支付 | `OrderPaidEvent` | 用户服务发短信；积分服务送积分 |
| 商家发货 | 发货 | 已发货 | `OrderShippedEvent` | 物流服务更新轨迹；用户服务发推送 |
| 用户取消 | 取消 | 已取消 | `OrderCancelledEvent` | 库存服务释放库存；支付服务发起退款 |

这张表我看一遍就能判断一个团队的设计成色。为什么？因为**它通篇是业务语言，产品经理能看懂、能确认、能挑错**。

我当年就吃过没画这张表的亏。最早写订单支付的时候，"发短信通知商家"是直接写在 `pay()` 方法里的。后来每加一个副作用——发推送、送积分、记流水——就要回头改领域方法。改到第三次我反应过来：领域方法根本不该认识短信、推送和积分。

换成事件之后就清爽了：`pay()` 只负责改状态、记事实，谁关心这个事实谁自己去订阅。加一个"下单送积分"，领域代码一行不动。

**这一步仍然不碰任何技术。** 我们不知道、也不关心这个事件将来是用 RocketMQ 发、用 Kafka 发，还是只在进程内同步分发。那是第五步的事。

---

## 用框架的构件把业务模型表达出来

业务动作梳理清楚了，接下来才轮到框架出场。

DDD 框架提供的那些构件——聚合根、实体、值对象、规则容器、领域事件、事件订阅、仓储——不是让你炫技的，每一个都对应上面梳理出来的一类东西：

| 构件 | 它承载的东西 |
| --- | --- |
| 聚合根 | 一致性边界，业务方法的唯一入口 |
| 实体 / 值对象 | 领域概念及其属性 |
| 规则容器（聚合之外） | 不变量，破坏即拒绝；错误码与文案集中注册 |
| 领域事件 | 已经发生的业务事实 |
| 事件订阅（契约 + 注册） | 跨聚合、跨上下文的后续动作 |
| 仓储接口 | 存取契约，此刻还没有实现 |

落到代码上，订单这一块拆成五个零件：**聚合根、规则容器、领域事件、事件订阅、仓储接口**。我们一个个看。

### 聚合根：只改状态、记操作、记事实

```java
// 聚合根：只暴露业务方法，setter 全部收敛为 protected
@Setter(AccessLevel.PROTECTED)
public class Order extends AggregateRoot<Long> {

    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private Address shippingAddress;
    private Money totalAmount;
    // 其余字段省略

    /** 标记订单已支付，同时发布订单支付事件。 */
    public void pay(PaymentInfo paymentInfo) {
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
        this.markModified();
        this.recordOperation(OrderOperationRegistry.PAY);
        this.collectEvent(OrderPaidEvent.buildEvent(this));
    }

    /** 取消订单，同时发布订单取消事件。 */
    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.markModified();
        this.recordOperation(OrderOperationRegistry.CANCEL);
        this.collectEvent(OrderCancelledEvent.buildEvent(this));
    }
}
```

注意 `pay()` 里**没有一行校验代码**。它不是不需要校验，而是校验被放到了独立的规则容器里。

### 规则：独立的容器，不在聚合根里

规则容器分成两半：一半是错误码注册表，一半是校验逻辑。

```java
// 消息注册表：错误码和文案集中声明，聚合根只负责指过来
public class OrderRuleRegistry extends BrokenRuleRegistry {
    public static final MessageCode ORDER_ADDRESS_REQUIRED =
            MessageCode.of("ORDER_ADDRESS_REQUIRED", "收货地址不能为空");
    public static final MessageCode ORDER_ADDRESS_CHANGE_INVALID =
            MessageCode.of("ORDER_ADDRESS_CHANGE_INVALID", "已发货的订单不能修改地址");
    public static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();
}
```

```java
// 规则容器：订单的不变量，构造时一次性注册
public class OrderRule extends EntityRule<Order> {

    public OrderRule() {
        // 收货地址必填：任何动作下都要成立
        this.addRule(
                order -> RuleCheckResult.of(order.getShippingAddress() != null),
                OrderRuleRegistry.ORDER_ADDRESS_REQUIRED);
        // 未发货才能改址：只在本次触发了「改址」这个动作时才校验
        this.addRule(
                order -> RuleCheckResult.of(order.getShipmentStatus() == ShipmentStatus.PENDING),
                OrderRuleRegistry.ORDER_ADDRESS_CHANGE_INVALID,
                IActiveRuleCondition.of(order ->
                        order.hasOperation(OrderOperationRegistry.CHANGE_ADDRESS)));
    }
}
```

两个设计点值得单拎出来：

- **规则容器是单例。** 它不持有任何 per-call 可变状态，所以能作为 Spring Bean 全局共享；聚合根只负责声明"我的错误码在哪儿"。
- **每条规则带激活条件。** "未发货才能改址"不该在支付时被触发，所以用 `hasOperation(...)` 判断本次动作，只对口的时候才参与校验。

另外，规则是在领域逻辑**执行之后**才跑的，所以"改之前是否已发货"这类要对比旧值的规则，读当前状态是读不出来的。这种情况覆盖 `requireOldEntity()` + `supplyOldEntity()` 拿一份旧快照即可，写法跟上面一致，只是 lambda 多一个 `old` 参数。

### 领域事件：过去式命名 + 静态工厂

```java
// 领域事件：已经发生的事实，命名用过去式；事件体只放少量上下文 ID
public class OrderPaidEvent extends BaseDomainEvent {

    private LocalDateTime paidAt;

    public OrderPaidEvent(String entityId) {
        super(entityId);
    }

    public static OrderPaidEvent buildEvent(Order order) {
        OrderPaidEvent event = new OrderPaidEvent(order.getEntityId().toString());
        event.paidAt = order.getPaidAt();
        return event;
    }
}
```

事件体只放**路由和上下文用的少量 ID**，订阅方要详细信息就按 `entityId` 回查聚合。事件体一旦跟着业务膨胀，加一个订阅方就得改一次事件定义，解耦又没了。

### 事件订阅：契约在领域层，实现在应用层，注册在配置里

```java
// 领域层：订阅契约，标注自己订阅哪个事件
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderPaidEvent")
public interface IOrderPaidSmsNotifyHandle
        extends IDomainService, IHandle<OrderPaidEvent> {
}
```

```java
// 应用层：实现契约，外部依赖全部走 dependency 端口
@Component
public class OrderPaidSmsNotifyHandle implements IOrderPaidSmsNotifyHandle {

    private final ISmsDependency smsDependency;

    @Override
    public void handleEvent(OrderPaidEvent event) {
        // 按 entityId 回查聚合，再发短信（细节省略）
        smsDependency.sendSms("您的订单已支付成功");
    }
}
```

```java
// 订阅关系集中注册：谁关心哪个事件，一眼看全
@Configuration
public class OrderEventSubscriberRegistry {

    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderPaidSmsNotifyHandle smsNotifyHandle,
                                        OrderPaidPointsGrantHandle pointsGrantHandle) {
        evtManager.registerSubscriber("sms-notify-on-order-paid", OrderPaidEvent.class, smsNotifyHandle);
        evtManager.registerSubscriber("points-grant-on-order-paid", OrderPaidEvent.class, pointsGrantHandle);
    }
}
```

### 仓储：此刻只有接口

```java
// 领域层：存取契约（接口以 I 开头）+ 抽象骨架，具体实现留给基础设施层
public interface IOrderRepository extends IRepository<Long, Order> {
}

public abstract class AbstractOrderRepository
        extends AbstractRepository<Long, Order> implements IOrderRepository {
}
```

**没有实现类，没有 SQL，没有表。** 这正是我们想要的——业务模型到此已经完整，它不依赖任何存储就能自洽。

### 应用层：把聚合、规则、仓储编排起来

命令的入参转换交给 `EntityUpdater`，编排本身只剩一行：

```java
// Input → 领域值对象的转换
@Component
public class OrderPayUpdater implements EntityUpdater<Order, PayOrderInput> {

    @Override
    public void apply(Order aggregateRoot, PayOrderInput command) {
        PaymentInfo paymentInfo = new PaymentInfo(command.getPaymentSerialNo(),
                new Money(command.getAmount(), command.getCurrency()));
        aggregateRoot.pay(paymentInfo);
    }
}
```

```java
// 应用服务：加载聚合 → 执行领域逻辑 → 规则校验 → 落库 → 发布事件
@Service
public class OrderWriteService extends AbstractApplicationService {

    public Order payOrder(Long orderId, PayOrderInput input) {
        Order order = orderRepository.findById(orderId);
        return super.execute(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input));
    }

    // 同一套规则还能「预跑」：不落库、不发事件，只返回结构化校验结果
    public DryRunResult tryPayOrder(Long orderId, PayOrderInput input) {
        Order order = orderRepository.findById(orderId);
        return super.tryExecute(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input));
    }
}
```

`execute(...)` 内部把"执行领域逻辑 → 跑规则 → 落库 → 发布事件"串成一个原子过程：规则不过就整体回滚，聚合不会被写脏。

这段代码里值得拎出来说的有三点：

**第一，`pay()` 里没有一行 SQL、没有一个 Redis 调用，也没有一行 `if (paymentStatus == PAID)`。** 它只做四件事：改状态、标记变更、记操作、记事实。校验由规则容器统一兜底——规则加一条、减一条，聚合根一行不动。

**第二，`collectEvent` 不等于"发消息"。** 它只是把事实登记在聚合上，至于这个事实最终落到哪个中间件，是基础设施层的事。这样领域层才能脱离技术独立测试——`pay()` 的单元测试不需要起容器、不需要连数据库。

**第三，订阅方可以随便加。** 新增"支付成功送积分"，就加一个 `IOrderPaidPointsGrantHandle` 契约、一个实现类、一行注册，领域代码零改动。这就是把事件订阅梳理清楚之后换来的扩展性。

顺带一提，规则外置还白送了一个能力：`tryExecute` 能在不落库、不发事件的前提下跑一遍全部规则，前端可以用它做"这个按钮能不能点"的预校验。规则要是写在聚合根里，这个能力基本做不出来。

---

## 存储是最后一步，不是第一步

业务模型定稿之后，再来看存储。这时候你会发现，每个存储该放什么东西，答案几乎是自动浮出来的：

- **MySQL**：聚合的持久化。事务只需要覆盖聚合边界内部，因为跨聚合的一致性我们已经交给事件了。
- **ES**：查询与检索用的读模型（投影），由事件订阅异步构建。
- **Redis**：热点数据、幂等键、分布式锁这类纯技术诉求。

于是你能做出一些传统思路下不敢做的决策：

- 订单聚合体存在 MySQL，但**订单列表页的查询走 ES 读模型**，不去扫聚合表。
- 物流轨迹干脆不进 MySQL，直接进 ES，天生适合按运单号检索。
- 订单的"是否已支付"这种高频判断，可以在 Redis 里放一份副本，由 `OrderPaidEvent` 事件驱动更新。

这些决策在传统设计思路下做不出来，不是因为想不到，而是**你已经被表结构绑死了**——表都建好了，你只会想着怎么往里塞字段，不会想着"这块数据其实根本不该存在这里"。

一句话总结这一步：**数据库只是聚合的一种持久化方案，不是聚合的定义来源。** 它存下 `status = 5`，却永远解释不了 5 是什么意思；能把 5 读成"已支付"的，只有业务模型。所以建模在前、存储在后，不是风格偏好，而是"意义必须先于符号"这件事本身决定的。

---

## 为什么先设计数据库等于白用了 DDD

最后说说反例，这个反例我自己演过。

前几年我做过一个订单重构，DDD 框架是上了的，聚合根、实体、领域事件一个不少。但我当时的顺序是：先把表设计好，再照着表写实体类。

结果写出来的 `Order` 是"平"的。最典型的一处，是收货地址。

收货地址本该是一个值对象，把省、市、区、详细地址、收件人、电话作为一个整体来表达：

```java
// 模型里：地址是一个值对象，一个概念、一个整体
public class Order extends AggregateRoot<Long> {
    private Address shippingAddress;
}
```

但如果你先设计数据库，地址在表里大概率会被摊成六个列：`province`、`city`、`district`、`detail`、`receiver_name`、`receiver_phone`。照着这张表倒推 `Order`，写出来的东西就是：

```java
public class Order extends AggregateRoot<Long> {
    private String province;
    private String city;
    private String district;
    private String detail;
    private String receiverName;
    private String receiverPhone;
}
```

注意发生了什么：**`Address` 这个概念，在聚合里彻底消失了。** 六个字段还在，可它们不再是"一个地址"，只是六根各自为政的字符串。于是"地址不能为空"变成六个字段挨个判空，"改地址"要把六个 setter 挨个调一遍，"校验地址是否合法"要在这六个字段上重新拼一遍——**值对象能整体封装、整体校验、整体替换的好处，一样都用不上了。**

这才是最要命的地方：字段一个没少，业务概念却没了。数据还在，语义丢了。而这，正是我开头说的那件事——**数据库只存数，数的意义由模型来定义**。地址的六个字段只是"数"，把它们重新组织成"一个地址"这个意义，是模型的责任，不是数据库的。

形式上是 DDD，骨子里还是数据驱动。

为什么会这样？因为**你是从表推导模型的**。表天然是平的、没有行为的，照着它推出来的实体，也必然是平的、没有行为的。

给你五条自检，随便挑一条就能试出成色：

1. 你的实体里，有没有 `public void setXxx()`？
2. 你的聚合根方法里，有没有内嵌的 `if (status == X)` 校验？（校验应该在规则容器里）
3. 你的值对象有没有被拆成平铺字段？地址是不是散成了 `province / city / district / receiverName` 这一串？
4. 你的仓储接口，返回的是不是数据库行对象的马甲？
5. 要加一个副作用（比如"下单送积分"），你需不需要改动领域方法的代码？

五题里命中两题，你大概率还停在老路上。别慌，这很正常——从大学学 SQL 开始，到工作里用代码生成器，我们接受的训练就是"数据驱动"。要改这个习惯，得有意识地练。

---

## 知识点总结

| 名称 | 说明 |
| --- | --- |
| 聚合根 | 一致性边界的守门人，外部只能通过它的业务方法改状态 |
| 实体 | 有 ID、有生命周期的领域概念，靠标识区分彼此 |
| 值对象 | 只关心值、不关心身份的概念，如 `Money`、`Address` |
| 业务规则 | 聚合在任何时刻都必须成立的不变量，破坏即拒绝 |
| 规则容器 | 聚合之外的独立规则集合（`EntityRule`），配消息注册表集中声明错误码，聚合根只声明"错误码在哪儿" |
| 操作码 | 记录"本次工作单元做了什么动作"，供规则按需激活，如 `PAY`、`CANCEL` |
| 领域事件 | 已经发生的业务事实，用过去式命名，如 `OrderPaidEvent` |
| 事件订阅 | 声明"谁关心这个事实"：契约在领域层、实现在应用层、注册在配置里 |
| 业务模型 | 聚合 + 规则 + 事件 + 订阅共同构成的、不依赖任何技术的模型 |
| 仓储接口 | 存取契约，在业务模型阶段只有接口与抽象骨架、没有实现 |
| 读模型 / 投影 | 由事件异步构建的查询视图，通常落在 ES 或缓存里 |
| 数据驱动 | 先建表再推模型的老思路，用了 DDD 框架也还是三层架构 |

---

## 自检与落地

**设计顺序自检清单**（按顺序打勾）

- [ ] 聚合根、实体、值对象是从需求里识别出来的，不是从表里倒推的
- [ ] 每个聚合的规则被单独列出来，且能在需求评审上被产品确认
- [ ] 规则写在独立的 `EntityRule` 容器里，错误码集中在消息注册表，聚合根里没有 `if` 校验
- [ ] 每个业务动作产生的领域事件都被列全，命名全部是过去式
- [ ] 每个事件都有明确的订阅方，没有"发出来没人管"的事件
- [ ] 仓储接口在业务模型定稿前没有任何实现
- [ ] 存储方案（表 / 索引 / 缓存结构）是最后才动手的

---
## 写在最后

DDD 不是银弹，它不会让你一夜之间写出完美的代码。

但它提供了一次很实在的顺序调整：**先设计业务模型，再设计存储。**

这个顺序的价值在于，它逼着你在设计阶段就直面业务的本质——这个系统到底要做什么、什么必须成立、什么事情发生了、谁关心它——而不是过早地掉进字段类型、索引和分库分表的细节里。

下次接到需求，试着忍住打开数据库工具的冲动。先拿起笔，在白板上把聚合根画出来，把规则、事件、事件订阅一行行列清楚。

等这张表被产品确认过，你会发现：建表反而是整个过程中最没悬念的一步。

毕竟，**连业务要做什么都没想清楚，表建得再漂亮，也是在给错误的问题修一条高速公路。**
