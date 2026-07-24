package io.pragmatic.ddd.event.spi;

import java.util.List;

/**
 * 事件订阅者顺序执行管理器（SPI）。
 *
 * <p>维护「同一事件下多个订阅者之间的执行顺序依赖图」：以虚拟根节点
 * {@value #ROOT_ALIAS} 为起点，记录每个订阅者必须在其前置依赖执行完成后
 * 才能被触发。事件管理器据此先触发根订阅者，待其完成后由其直接后继继续
 * 传播，形成一条顺序执行链。</p>
 *
 * @author lixiaojing10
 */
public interface ISubscriberOrderManager {

    /** 虚拟根节点别名：无显式依赖的订阅者都挂在根节点下。 */
    String ROOT_ALIAS = "_root_";

    /**
     * 注册订阅者之间的依赖关系（依赖边）。
     *
     * @param eventName              事件名
     * @param subscriberAlias        当前订阅者别名（被触发的后继）
     * @param dependSubscriberAlias  其前置依赖订阅者别名；为 {@code null}/空白/{@link #ROOT_ALIAS} 表示无依赖（根订阅者）
     */
    void registerDependency(String eventName, String subscriberAlias, String dependSubscriberAlias);

    /** 返回某事件的所有根订阅者（无前置依赖）。 */
    List<String> findRootSubscribers(String eventName);

    /** 返回某订阅者执行完成后应触发的直接后继订阅者。 */
    List<String> findNextSubscribers(String eventName, String subscriberAlias);

    /** 返回某事件的全部依赖边，用于可视化/调试。 */
    List<OrderEdge> getDependencyEdges(String eventName);

    /**
     * 依赖边：{@code predecessor} 为前置订阅者，{@code successor} 为后继订阅者。
     */
    record OrderEdge(String predecessor, String successor) {
    }
}
