package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 实体工厂 —— 从 Command DTO 构建一个新的聚合根实例。
 *
 * <p>定位：应用服务层的"计算编排入口"契约。按字段调用
 * {@link FieldResolver} 完成计算，通过 {@link io.pragmatic.ddd.base.IParamObject}
 * 参数对象传入实体。实体内部只做纯赋值，无计算逻辑。</p>
 *
 * <p><b>核心约定："先算后赋"</b></p>
 * <ol>
 *   <li><b>阶段 1（计算）</b>：简单字段直接取值，复杂字段通过 FieldResolver 计算，
 *       每个字段拿到最终值。</li>
 *   <li><b>阶段 2（赋值）</b>：将计算结果组装成 IParamObject 参数对象，
 *       传入实体构造函数。实体内部纯赋值，无任何计算或 if-else 判断。</li>
 * </ol>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   public class OrderFactory implements EntityFactory<Order, CreateOrderCommand> {
 *
 *       // 第三个类型参数是实体（创建场景尚无实体，resolve(cmd) 内部 entity 为 null）
 *       private final FieldResolver<CreateOrderCommand, Order, Long> orderIdResolver;
 *       private final FieldResolver<CreateOrderCommand, Order, BigDecimal> totalPriceResolver;
 *       private final FieldResolver<CreateOrderCommand, Order, String> addressResolver;
 *
 *       public Order create(CreateOrderCommand cmd) {
 *           // ===== 阶段 1：计算（按字段调用 Resolver） =====
 *           long orderId          = orderIdResolver.resolve(cmd);
 *           BigDecimal totalPrice = totalPriceResolver.resolve(cmd);
 *           String address        = addressResolver.resolve(cmd);
 *
 *           // ===== 阶段 2：赋值（纯赋值） =====
 *           OrderInitParam param = new OrderInitParam();
 *           param.setOrderId(orderId);
 *           param.setTotalPrice(totalPrice);
 *           param.setAddress(address);
 *           param.setComment(cmd.getComment());   // 简单字段：直接赋值
 *           param.setPin(cmd.getPin());           // 简单字段：直接赋值
 *           return new Order(param);
 *       }
 *   }
 * }</pre>
 *
 * @param <T> 聚合根类型
 * @param <C> Command DTO 类型
 * @author Li XiaoJing
 * @since 2.2.0
 */
public interface EntityFactory<T extends AggregateRoot<?>, C> {

    /**
     * 从 Command DTO 构建一个新的聚合根实例。
     *
     * <p>实现规范：</p>
     * <ul>
     *   <li>上方集中完成所有计算逻辑（领域服务调用、多字段结合、默认值处理）</li>
     *   <li>下方组装 IParamObject 参数对象，传入实体构造函数</li>
     *   <li>复杂的计算逻辑抽取为私有方法，保持 create() 方法结构清晰</li>
     * </ul>
     *
     * @param command Command DTO
     * @return 新创建的聚合根实例
     */
    T create(C command);
}
