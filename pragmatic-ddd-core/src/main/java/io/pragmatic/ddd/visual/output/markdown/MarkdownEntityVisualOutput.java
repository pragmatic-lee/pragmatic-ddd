package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.entity.IEntityVisualOutput;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;
import java.util.stream.Collectors;

public class MarkdownEntityVisualOutput implements IEntityVisualOutput {

    private static final String START = "```mermaid" + SystemUtils.LINE_SEPARATOR;
    private static final String END = "```" + SystemUtils.LINE_SEPARATOR;


    private static final String CLASS_MARK_STRING = "classDiagram" + SystemUtils.LINE_SEPARATOR;

    @Override
    public String output(List<EntityDescriptor> entityDescriptorList) {

        StringBuilder markDownString = new StringBuilder();
        markDownString.append(START);
        markDownString.append(CLASS_MARK_STRING);

        entityDescriptorList.forEach(et -> {
            String s = this.buildClass(et);
            markDownString.append(s);
        });

        String relation = this.buildClassRelation(entityDescriptorList);
        markDownString.append(relation);


        markDownString.append(END);

        return markDownString.toString();
    }

    private String buildClassRelation(List<EntityDescriptor> entityDescriptorList) {

        List<String> allClass = this.allClass(entityDescriptorList);

        StringBuilder classRelationStringBuilder = new StringBuilder();

        entityDescriptorList.forEach(et -> et.getFieldInfoList().forEach(f -> {
            if (allClass.contains(f.getType())) {

                classRelationStringBuilder.append(et.getClsName());
                if (f.collection) {
                    classRelationStringBuilder.append(" \"1\" --> \"*\" ");
                } else {
                    classRelationStringBuilder.append(" \"1\" --> \"1\" ");
                }
                classRelationStringBuilder.append(f.getType());
                classRelationStringBuilder.append(SystemUtils.LINE_SEPARATOR);
            }
        }));

        return classRelationStringBuilder.toString();

    }

    private List<String> allClass(List<EntityDescriptor> entityDescriptorList) {
        return entityDescriptorList.stream().map(EntityDescriptor::getClsName).collect(Collectors.toList());
    }


    private String buildClass(EntityDescriptor entityDescriptor) {
        StringBuilder classStringBuilder = new StringBuilder();

        classStringBuilder.append("class ");
        classStringBuilder.append(entityDescriptor.getClsName());
        classStringBuilder.append("[\"");
        classStringBuilder.append(entityDescriptor.getClsName());
        classStringBuilder.append("(");
        classStringBuilder.append(entityDescriptor.getDescription());
        classStringBuilder.append(")");
        classStringBuilder.append("\"]");
        classStringBuilder.append(SystemUtils.LINE_SEPARATOR);

        entityDescriptor.getFieldInfoList().forEach(f -> {

            classStringBuilder.append(entityDescriptor.getClsName());
            classStringBuilder.append(" : ");
            classStringBuilder.append("+");
            classStringBuilder.append(f.getType());
            classStringBuilder.append(" ");
            classStringBuilder.append(f.getFieldName());
            classStringBuilder.append("[");
            classStringBuilder.append(f.getDescription());
            classStringBuilder.append("]");
            classStringBuilder.append(SystemUtils.LINE_SEPARATOR);

        });

        if (Boolean.TRUE.equals(entityDescriptor.getRoot())) {
            entityDescriptor.getEntityActionDescriptorList().forEach(action -> {
                classStringBuilder.append(entityDescriptor.getClsName());
                classStringBuilder.append(" : ");
                classStringBuilder.append("+");
                classStringBuilder.append(action.getMethodName());
                classStringBuilder.append("()");
                classStringBuilder.append(SystemUtils.LINE_SEPARATOR);
            });
        }
        return classStringBuilder.toString();
    }
}
