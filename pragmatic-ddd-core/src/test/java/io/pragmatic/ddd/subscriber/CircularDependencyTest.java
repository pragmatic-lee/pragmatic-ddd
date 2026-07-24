package io.pragmatic.ddd.subscriber;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 循环依赖判断
 *
 * @author lixiaojing10
 * @date 2021/12/28 9:59 下午
 */
public class CircularDependencyTest {


    @Test
    public void check() {

        //每次都从root进行检查,是否有循环依赖
        List<DepData> collect = Stream.of(
                new DepData(1, 2),
                new DepData(2, 3),
                new DepData(1, 4),
                new DepData(4, 5),
                new DepData(4, 6),
                new DepData(6, 7),
                new DepData(6, 1)
//                new DepData(7, 5)
        ).collect(Collectors.toList());


//        List<DepData> collect = Stream.of(
//                new DepData(1, 2),
//                new DepData(2, 1)).collect(Collectors.toList());


        //root
        DependantTreeItem root = new DependantTreeItem(1, new ArrayList<>());

        //父节点引用计数，key = 指定节点 value=父节点
        HashMap<Integer, List<Integer>> parentCounter = new HashMap<>();
        //循环依赖引用计数， key=指定的节点 内部key = 子节点 ,内部 value=依赖计数
        HashMap<Integer, HashMap<Integer, AtomicInteger>> childCounter = new HashMap<>();


        this.dependCheck(root, null, collect, parentCounter, childCounter);

        System.out.println("xxxx");

    }

    //如果有循环依赖这块可以报错
    private void dependCheck(DependantTreeItem depDataItem, DependantTreeItem parent, List<DepData> depDataList, HashMap<Integer, List<Integer>> counter, HashMap<Integer, HashMap<Integer, AtomicInteger>> childCounter) {

        if (parent != null) {
            if (childCounter.containsKey(parent.current)) {
                if (childCounter.get(parent.current).containsKey(depDataItem.current)) {
                    int i = childCounter.get(parent.current).get(depDataItem.current).incrementAndGet();
                    if (i > 1) {
                        throw new RuntimeException(parent.current + "有循环依赖");
                    }
                }
            } else {

                childCounter.computeIfAbsent(parent.current, s -> {

                    HashMap<Integer, AtomicInteger> child = new HashMap<>();
                    child.put(depDataItem.current, new AtomicInteger());
                    int i = child.get(depDataItem.current).incrementAndGet();
                    if (i > 1) {
                        throw new RuntimeException(parent.current + "有循环依赖");
                    }
                    return child;

                });
            }
        }


        List<DependantTreeItem> collect = depDataList.stream().filter(s -> s.current == depDataItem.current).map(s -> new DependantTreeItem(s.child, new ArrayList<>())).collect(Collectors.toList());
        depDataItem.childList.addAll(collect);
        collect.forEach(s -> {
            this.dependCheck(s, depDataItem, depDataList, counter, childCounter);
        });

        if (counter.containsKey(depDataItem.current)) {
            counter.get(depDataItem.current).add(Optional.ofNullable(parent).map(t -> t.current).orElse(0));
            if (counter.get(depDataItem.current).size() > 1) {
                String joinString = counter.get(depDataItem.current).stream().map(Object::toString).collect(Collectors.joining(","));
                throw new RuntimeException(depDataItem.current + "有多个父节点" + joinString);
            }
        } else {
            counter.computeIfAbsent(depDataItem.current, s -> {
                ArrayList<Integer> integers = new ArrayList<>();
                integers.add(Optional.ofNullable(parent).map(t -> t.current).orElse(0));
                return integers;
            });
        }
    }
}

class DependantTreeItem {
    public final int current;
    public final List<DependantTreeItem> childList;

    public DependantTreeItem(int current, List<DependantTreeItem> childList) {
        this.current = current;
        this.childList = childList;
    }
}


class DepData {

    public int getCurrent() {
        return current;
    }

    public final int current;
    public final int child;

    public DepData(int current, int child) {
        this.current = current;
        this.child = child;
    }
}
