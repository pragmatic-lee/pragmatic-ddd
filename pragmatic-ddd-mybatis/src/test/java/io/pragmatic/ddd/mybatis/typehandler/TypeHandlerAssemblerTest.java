package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.base.IValueObject;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 装配器单测：多份声明合并、作用域校验与全部声明冲突的 fail-fast 行为。
 *
 * @author wizard-lee
 */
@DisplayName("TypeHandler 装配器合并与校验")
class TypeHandlerAssemblerTest {

    enum Color implements IEnumValue<Integer, Color> {
        RED(10, "红"), GREEN(20, "绿");

        private final int code;
        private final String label;

        Color(int code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    enum Channel implements IEnumValue<String, Channel> {
        APP("app", "应用"), WEB("web", "网站");

        private final String code;
        private final String label;

        Channel(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    /** 既是值对象又是枚举：用于构造「同时出现在两个通道」的声明矛盾。 */
    enum VoEnum implements IValueObject, IEnumValue<Integer, VoEnum> {
        A(1, "a");

        private final int code;
        private final String label;

        VoEnum(int code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    static class Profile implements IValueObject {
        private String nickname;

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    static class OrderRow {
    }

    static class MemberRow {
    }

    @Test
    @DisplayName("多份声明合并为一份上下文，三个通道全覆盖")
    void mergesAllDeclarations() {
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(orderDeclaration(), memberDeclaration()),
                JdbcJsonValue.DEFAULT);

        assertThat(javaTypes(context))
                .contains(Color.class, Channel.class, Profile.class, List.class);
        assertThat(context.enumRules())
                .containsEntry(Color.class, EnumRule.CODE)
                .containsEntry(Channel.class, EnumRule.NAME);
        assertThat(context.collections().columnListTypes())
                .containsKeys("order_items", "member_tags");
    }

    @Test
    @DisplayName("恰好一个集合列时集合处理器仍可还原元素类型")
    void mergedListHandlerRestoresElementType() {
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(orderDeclaration(), memberDeclaration()),
                JdbcJsonValue.DEFAULT);

        assertThat(context.collections().columnListTypes().get("order_items").getTypeName())
                .contains("List")
                .contains(Color.class.getTypeName());
    }

    @Test
    @DisplayName("同枚举被声明为两种策略时启动失败")
    void conflictingEnumRuleFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(
                        TypeHandlerDeclaration.of(Map.of(Color.class, EnumRule.CODE), List.of(), List.of()),
                        TypeHandlerDeclaration.of(Map.of(Color.class, EnumRule.NAME), List.of(), List.of())),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("枚举持久化策略冲突");
    }

    @Test
    @DisplayName("同一类型同时出现在枚举与值对象声明中时启动失败")
    void sameTypeInBothChannelsFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(
                        Map.of(VoEnum.class, EnumRule.NAME), List.of(VoEnum.class), List.of())),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("声明矛盾");
    }

    @Test
    @DisplayName("值对象未实现 IValueObject 时启动失败")
    void invalidValueObjectFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(Map.of(), List.of(String.class), List.of())),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("值对象声明非法");
    }

    @Test
    @DisplayName("枚举声明中出现非枚举类型时启动失败")
    void invalidEnumDeclarationFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(Map.of(String.class, EnumRule.CODE), List.of(), List.of())),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("枚举声明非法");
    }

    @Test
    @DisplayName("集合元素枚举未显式声明策略时启动失败")
    void enumElementWithoutRuleFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(
                        Map.of(),
                        List.of(),
                        List.of(CollectionMapping.of(OrderRow.class, "items", Color.class).build()))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("集合元素枚举未声明持久化策略");
    }

    @Test
    @DisplayName("同列标签被声明为两种元素类型时启动失败")
    void sameLabelDifferentTypeFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(
                                CollectionMapping.of(OrderRow.class, "tags", String.class)
                                        .columnLabel("tags").build())),
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(
                                CollectionMapping.of(MemberRow.class, "tags", Integer.class)
                                        .columnLabel("tags").build()))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("集合列标签冲突");
    }

    @Test
    @DisplayName("同列标签被声明为两种转换器时启动失败")
    void sameLabelDifferentConverterFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(
                                CollectionMapping.of(OrderRow.class, "tags", String.class)
                                        .columnLabel("tags")
                                        .converter(value -> value.toString().toUpperCase()).build())),
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(
                                CollectionMapping.of(MemberRow.class, "tags", String.class)
                                        .columnLabel("tags")
                                        .converter(value -> value.toString().toLowerCase()).build()))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("集合列标签转换器冲突");
    }

    @Test
    @DisplayName("非默认作用域尚未接入装配入口，启动失败")
    void nonDefaultScopeFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(orderDeclaration().withScope("report")),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("多数据源作用域尚未接入装配入口");
    }

    @Test
    @DisplayName("空声明聚合不污染有集合声明的聚合")
    void emptyDeclarationDoesNotPollute() {
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.empty(), orderDeclaration()),
                JdbcJsonValue.DEFAULT);

        assertThat(context.collections().columnListTypes()).containsKey("order_items");
        assertThat(javaTypes(context)).contains(List.class);
    }

    @Test
    @DisplayName("没有任何集合声明时不注册 List 处理器")
    void noCollectionDeclarationKeepsListUnregistered() {
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(Map.of(Color.class, EnumRule.CODE), List.of(), List.of())),
                JdbcJsonValue.DEFAULT);

        assertThat(javaTypes(context)).doesNotContain(List.class);
        assertThat(context.collections().columnListTypes()).isEmpty();
    }

    private List<Class<?>> javaTypes(TypeHandlerContext context) {
        return context.registrations().stream()
                .map(TypeHandlerRegistration::javaType)
                .toList();
    }

    private TypeHandlerDeclaration orderDeclaration() {
        return TypeHandlerDeclaration.of(
                Map.of(Color.class, EnumRule.CODE),
                List.of(Profile.class),
                List.of(CollectionMapping.of(OrderRow.class, "items", Color.class)
                        .columnLabel("order_items").build()));
    }

    private TypeHandlerDeclaration memberDeclaration() {
        return TypeHandlerDeclaration.of(
                Map.of(Channel.class, EnumRule.NAME),
                List.of(),
                List.of(CollectionMapping.of(MemberRow.class, "tags", String.class)
                        .columnLabel("member_tags").build()));
    }
}
