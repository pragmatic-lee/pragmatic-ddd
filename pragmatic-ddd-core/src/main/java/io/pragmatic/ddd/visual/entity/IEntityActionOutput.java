package io.pragmatic.ddd.visual.entity;

/**
 * 实体行为可视化输出 —— 将实体行为描述符渲染为文本。
 *
 * @author wizard-lee
 */
public interface IEntityActionOutput {

    /** 将实体行为描述符渲染为文本。 */
    String output(EntityDescriptor entityDescriptor);
}
