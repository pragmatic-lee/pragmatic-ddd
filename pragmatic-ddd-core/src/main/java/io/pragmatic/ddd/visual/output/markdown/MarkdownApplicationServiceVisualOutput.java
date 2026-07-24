package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.application.ApplicationDescriptor;
import io.pragmatic.ddd.visual.application.IApplicationServiceVisualOutput;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;

public class MarkdownApplicationServiceVisualOutput implements IApplicationServiceVisualOutput {
    @Override
    public String output(List<ApplicationDescriptor> applicationDescriptorList) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("|类名|方法|类型|描述|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("|-|-|-|-|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);


        applicationDescriptorList.forEach(a->{

            String s = this.buildRow(a);
            stringBuilder.append(s);
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        });



        return stringBuilder.toString();
    }

    private String buildRow(ApplicationDescriptor applicationDescriptor) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("|");
        stringBuilder.append(applicationDescriptor.getClsName());
        stringBuilder.append("|");
        stringBuilder.append(applicationDescriptor.getMethodName());
        stringBuilder.append("|");
        stringBuilder.append(applicationDescriptor.getType());
        stringBuilder.append("|");
        stringBuilder.append(applicationDescriptor.getApplicationServiceDescription());
        stringBuilder.append("|");

        return stringBuilder.toString();
    }
}
