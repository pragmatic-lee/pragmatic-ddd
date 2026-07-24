package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link ISubscriberOrderManager} 的默认内存实现。
 * 以「事件名 → 依赖边集合（并发安全）」维护订阅者顺序图，并在注册时检测循环依赖。
 *
 * @author lixiaojing10
 */
public class SubscriberOrderManager implements ISubscriberOrderManager {

    /** 事件名 → 依赖边集合（value 使用并发 Set，保证注册线程安全）。 */
    private final ConcurrentMap<String, Set<OrderEdge>> edges = new ConcurrentHashMap<>();

    @Override
    public void registerDependency(String eventName, String subscriberAlias, String dependSubscriberAlias) {
        String predecessor = (dependSubscriberAlias == null || dependSubscriberAlias.isBlank())
                ? ROOT_ALIAS : dependSubscriberAlias;

        if (predecessor.equals(subscriberAlias)) {
            throw new IllegalArgumentException("Subscriber cannot depend on itself: " + subscriberAlias);
        }

        Set<OrderEdge> set = edges.computeIfAbsent(eventName, k -> ConcurrentHashMap.newKeySet());
        // 循环依赖检测：若 predecessor 经后继链能回到 subscriberAlias，则新增边会成环
        if (wouldCreateCycle(set, predecessor, subscriberAlias)) {
            throw new IllegalStateException(
                    "Cyclic dependency detected: " + subscriberAlias + " -> " + predecessor);
        }
        set.add(new OrderEdge(predecessor, subscriberAlias));
    }

    @Override
    public List<String> findNextSubscribers(String eventName, String subscriberAlias) {
        Set<OrderEdge> set = edges.get(eventName);
        if (set == null) {
            return Collections.emptyList();
        }
        List<String> successors = new ArrayList<>();
        for (OrderEdge edge : set) {
            if (edge.predecessor().equals(subscriberAlias)) {
                successors.add(edge.successor());
            }
        }
        return successors;
    }

    @Override
    public List<String> findRootSubscribers(String eventName) {
        return findNextSubscribers(eventName, ROOT_ALIAS);
    }

    @Override
    public List<OrderEdge> getDependencyEdges(String eventName) {
        Set<OrderEdge> set = edges.get(eventName);
        return set == null ? Collections.emptyList() : new ArrayList<>(set);
    }

    /** 新增边 from->to 是否会成环：若从 to 沿已有后继可到达 from，则成环。 */
    private boolean wouldCreateCycle(Set<OrderEdge> set, String from, String to) {
        Set<String> visited = ConcurrentHashMap.newKeySet();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(to);
        while (!stack.isEmpty()) {
            String node = stack.pop();
            if (node.equals(from)) {
                return true;
            }
            if (!visited.add(node)) {
                continue;
            }
            for (OrderEdge edge : set) {
                if (edge.predecessor().equals(node)) {
                    stack.push(edge.successor());
                }
            }
        }
        return false;
    }
}
