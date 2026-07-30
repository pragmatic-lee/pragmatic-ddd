package io.pragmatic.ddd.event.spi;

/**
 * 事件管理器生命周期接口。
 *
 * @author wizard-lee
 */
public interface IEventLifecycle {

    /** 初始化。 */
    void init();

    /** 启动。 */
    void start();

    /** 关闭。 */
    void shutdown();
}
