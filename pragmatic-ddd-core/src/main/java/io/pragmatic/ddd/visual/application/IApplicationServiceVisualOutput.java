package io.pragmatic.ddd.visual.application;

import java.util.List;

/**
 * 应用服务可视化输出 —— 将应用服务描述符列表渲染为文本。
 *
 * @author wizard-lee
 */
public interface IApplicationServiceVisualOutput {

    /** 将应用服务描述符列表渲染为文本。 */
    String output(List<ApplicationDescriptor> applicationDescriptorList);
}
