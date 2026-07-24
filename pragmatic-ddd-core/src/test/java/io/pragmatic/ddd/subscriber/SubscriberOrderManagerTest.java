package io.pragmatic.ddd.subscriber;

import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author lixiaojing10
 * @date 2021/12/23 3:44 下午
 */
public class SubscriberOrderManagerTest {

    @Test
    public void registerDependencyTest() {
        SubscriberOrderManager manager = new SubscriberOrderManager();
        manager.registerDependency("evt", "a", null);
        manager.registerDependency("evt", "b", null);
        manager.registerDependency("evt", "c", null);
        manager.registerDependency("evt", "d", "a");
        manager.registerDependency("evt", "e", "a");

        List<String> evt = manager.findRootSubscribers("evt");
        Assert.assertEquals(3, evt.size());

        List<String> strings = manager.findNextSubscribers("evt", "a");

        Assert.assertEquals(2, strings.size());
        Assert.assertTrue(strings.contains("d"));
        Assert.assertTrue(strings.contains("e"));
    }

    @Test(expected = IllegalStateException.class)
    public void cyclicDependencyTest() {
        SubscriberOrderManager manager = new SubscriberOrderManager();
        manager.registerDependency("evt", "a", "b");
        manager.registerDependency("evt", "b", "a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void selfDependencyTest() {
        SubscriberOrderManager manager = new SubscriberOrderManager();
        manager.registerDependency("evt", "a", "a");
    }
}
