package io.pragmatic.ddd.application.compensation;

/**
 * 补偿编排器入口（SPI）：默认提供本地内存实现，可替换为外部框架适配实现。
 *
 * @author wizard-lee
 */
public interface ICompensationManager {

    /** 以默认选项（内存模式）开启一个补偿范围。 */
    ICompensationScope begin();

    /** 以指定选项开启一个补偿范围。 */
    ICompensationScope begin(CompensationOptions options);
}
