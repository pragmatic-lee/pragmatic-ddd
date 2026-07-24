package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.visual.EntityItem;
import io.pragmatic.ddd.visual.MockEntity;
import io.pragmatic.ddd.visual.output.markdown.MarkDownEnumValueVisualOutput;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntityTest {

    @Test
    public void TestEnumTest(){
        // 静态 toParse 已移除；core 不依赖 mybatis，集中解析由 EnumValueResolver 在 mybatis 模块提供
    }

    @Test
    public void testField() {

        AbstractEntityFieldFinder abstractEntityFieldFinder = new AbstractEntityFieldFinder() {
            @Override
            protected void initFieldList() {
//                addField(MockEntity::getId,"业务唯一标识");
//                addField(MockEntity::getName, "名称");
//                addField(MockEntity::getAgeTest, "年龄");
//                addField(MockEntity::getAge, "年龄");
                addField(MockEntity::getMockValueObject, "模拟值对象");
//                addField(MockValueObject::getName, "模拟值对象名称");
//                addField(MockValueObject::isYes, "模拟值对象Yes");
//                addField(MockEntity::getEntityItems, "集合测试", EntityItem.class);
                addField(EntityItem::getA, "A");
            }
        };

        EntityParser entityParser = new EntityParser();
        entityParser.registerEntity(MockEntity.class, abstractEntityFieldFinder);


        List<EntityDescriptor> parse = entityParser.parse(MockEntity.class);

        System.out.println(JSON.toJSONString(parse, JSONWriter.Feature.PrettyFormat));


    }

    @Test
    public void actionParamTest() {
        EntityTest2 entityTest2 = new EntityTest2(1L, "张三");

        entityTest2.updateForUse("李四");

        Boolean validate = entityTest2.satisfiesRule(new EntityTest2EntityRule());



    }

    @Test
    public void enumTest(){

        EnumValueParser enumValueParser = new EnumValueParser();
        enumValueParser.registerEnum(MockEntity.class, new IEnumValueFinder() {
            @Override
            public List<Class<?>> findEnums() {
                List<Class<?>> iEnumValues = new ArrayList<>();
                iEnumValues.add(TestEnum.class);
                return iEnumValues;
            }
        });

        List<EnumInfoDescriptor> parse = enumValueParser.parse(MockEntity.class);

        System.out.println(JSON.toJSONString(parse, JSONWriter.Feature.PrettyFormat));

        MarkDownEnumValueVisualOutput out = new MarkDownEnumValueVisualOutput();

        String output = out.output(parse);
        System.out.println(output);
    }

    @Test
    public void regXTest() {
        String test = "(Lcn/easylib/domain/visual/MockEntity;)Ljava/lang/Long;";


        Pattern compile = Pattern.compile("\\(L(.+);\\)");

        Matcher matcher = compile.matcher(test);

        System.out.println(matcher.group(1));

    }
}

