package io.pragmatic.ddd.visual.entity;

import java.util.List;

/**
 * 枚举值可视化输出 —— 将枚举信息描述符列表渲染为文本。
 *
 * @author wizard-lee
 */
public interface IEnumValueVisualOutput {

    /** 将枚举信息描述符列表渲染为文本。 */
    String output(List<EnumInfoDescriptor> enumInfoDescriptorList);
}
