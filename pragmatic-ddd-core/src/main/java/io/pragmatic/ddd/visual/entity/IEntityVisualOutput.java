package io.pragmatic.ddd.visual.entity;

import java.util.List;

/**
 * 实体可视化输出 —— 将实体描述符列表渲染为文本。
 *
 * @author wizard-lee
 */
public interface IEntityVisualOutput {
    /** 将实体描述符列表渲染为文本。 */
    String output(List<EntityDescriptor> entityDescriptorList);
}
