package io.pragmatic.ddd.visual;

/**
 * 全量视图可视化输出 —— 将完整领域模型信息渲染为单一文本。
 *
 * @author wizard-lee
 */
public interface IFullViewVisualOutput {

    /** 将领域模型可视化信息渲染为文本。 */
    String output(DomainModelVisualInfo domainModelVisualInfo);
}
