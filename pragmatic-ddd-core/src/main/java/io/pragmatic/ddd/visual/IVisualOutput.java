package io.pragmatic.ddd.visual;

/**
 * 可视化输出契约 —— 将领域模型信息渲染为文本。
 *
 * @author wizard-lee
 */
public interface IVisualOutput {

    /** 将领域模型可视化信息渲染为文本。 */
    String output(DomainModelVisualInfo domainModelVisualInfo);
}
