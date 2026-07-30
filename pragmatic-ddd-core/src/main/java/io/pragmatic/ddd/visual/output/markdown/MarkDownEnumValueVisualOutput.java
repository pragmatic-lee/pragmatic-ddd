package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.entity.EnumInfoDescriptor;
import io.pragmatic.ddd.visual.entity.EnumValue;
import io.pragmatic.ddd.visual.entity.IEnumValueVisualOutput;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;

/**
 * 枚举值 Markdown 输出 —— 将枚举信息描述符列表渲染为 Markdown 表格。
 *
 * @author wizard-lee
 */
public class MarkDownEnumValueVisualOutput implements IEnumValueVisualOutput {
    /** 将枚举信息描述符列表渲染为 Markdown 文本。 */
    @Override
    public String output(List<EnumInfoDescriptor> enumInfoDescriptorList) {

        StringBuilder stringBuilder = new StringBuilder();
        enumInfoDescriptorList.forEach(e -> {
            String s = this.buildEnumValues(e.getName(), e.getValueList());
            stringBuilder.append(s);
        });

        return stringBuilder.toString();
    }

    private String buildEnumValues(String name, List<EnumValue> enumValues) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("### *").append(name);
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("|名称|值|描述|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("|-|-|-|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        enumValues.forEach(e -> {

            stringBuilder.append("|");
            stringBuilder.append(e.getName());
            stringBuilder.append("|");
            stringBuilder.append(e.getValue());
            stringBuilder.append("|");
            stringBuilder.append(e.getDescription());
            stringBuilder.append("|");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        });
        return stringBuilder.toString();

    }
}
