package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.base.IValueObject;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionMapping;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    /** 泛型可解析的自定义 handler：TypeHandler&lt;String&gt;。 */
    static class MoneyHandler extends BaseTypeHandler<String> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) {
        }

        @Override
        public String getNullableResult(ResultSet rs, String columnName) {
            return null;
        }

        @Override
        public String getNullableResult(ResultSet rs, int columnIndex) {
            return null;
        }

        @Override
        public String getNullableResult(CallableStatement cs, int columnIndex) {
            return null;
        }
    }

    /** 泛型可解析的自定义 handler：TypeHandler&lt;BigDecimal&gt;。 */
    static class BigDecimalHandler extends BaseTypeHandler<BigDecimal> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, BigDecimal parameter, JdbcType jdbcType) {
        }

        @Override
        public BigDecimal getNullableResult(ResultSet rs, String columnName) {
            return null;
        }

        @Override
        public BigDecimal getNullableResult(ResultSet rs, int columnIndex) {
            return null;
        }

        @Override
        public BigDecimal getNullableResult(CallableStatement cs, int columnIndex) {
            return null;
        }
    }

    /** 泛型可解析的自定义 handler：TypeHandler&lt;List&gt;，供 List.class 补位用例使用。 */
    static class AnyListHandler extends BaseTypeHandler<List> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, List parameter, JdbcType jdbcType) {
        }

        @Override
        public List getNullableResult(ResultSet rs, String columnName) {
            return null;
        }

        @Override
        public List getNullableResult(ResultSet rs, int columnIndex) {
            return null;
        }

        @Override
        public List getNullableResult(CallableStatement cs, int columnIndex) {
            return null;
        }
    }

    /** raw 实现：TypeHandler 泛型不可解析，R13 跳过校验。 */
    static class RawHandler implements TypeHandler {

        @Override
        public void setParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) {
        }

        @Override
        public Object getResult(ResultSet rs, String columnName) {
            return null;
        }

        @Override
        public Object getResult(ResultSet rs, int columnIndex) {
            return null;
        }

        @Override
        public Object getResult(CallableStatement cs, int columnIndex) {
            return null;
        }
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

    @Test
    @DisplayName("两份声明各含一个自定义 handler 时合并注册（T13）")
    void mergesCustomHandlers() {
        MoneyHandler first = new MoneyHandler();
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                                List.of(new CustomTypeHandlerMapping(String.class, first))),
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                                List.of(new CustomTypeHandlerMapping(BigDecimal.class, new BigDecimalHandler())))),
                JdbcJsonValue.DEFAULT);

        assertThat(javaTypes(context)).contains(String.class, BigDecimal.class);
        assertThat(handlerOf(context, String.class)).isSameAs(first);
        assertThat(handlerOf(context, BigDecimal.class)).isInstanceOf(BigDecimalHandler.class);
    }

    @Test
    @DisplayName("自定义键命中枚举通道时启动失败并指路（T14）")
    void customHandlerClashingEnumChannelFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(
                        Map.of(Color.class, EnumRule.CODE), List.of(), List.of(),
                        List.of(new CustomTypeHandlerMapping(Color.class, new MoneyHandler())))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("与枚举通道冲突")
                .hasMessageContaining("调整 enumRules");
    }

    @Test
    @DisplayName("自定义键命中值对象通道时启动失败并指路（T15）")
    void customHandlerClashingVoChannelFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(
                        Map.of(), List.of(Profile.class), List.of(),
                        List.of(new CustomTypeHandlerMapping(Profile.class, new MoneyHandler())))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("与值对象通道冲突")
                .hasMessageContaining("JsonSerializer");
    }

    @Test
    @DisplayName("有集合声明时自定义 List.class 启动失败并指路（T16）")
    void customHandlerClashingListChannelFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(
                        Map.of(), List.of(),
                        List.of(CollectionMapping.of(OrderRow.class, "items", String.class).build()),
                        List.of(new CustomTypeHandlerMapping(List.class, new MoneyHandler())))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("与集合通道冲突")
                .hasMessageContaining("ElementConverter");
    }

    @Test
    @DisplayName("无集合声明时自定义 List.class 放行（补位语义，T17）")
    void customListHandlerWithoutCollectionsSucceeds() {
        AnyListHandler handler = new AnyListHandler();
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(
                        Map.of(), List.of(), List.of(),
                        List.of(new CustomTypeHandlerMapping(List.class, handler)))),
                JdbcJsonValue.DEFAULT);

        assertThat(javaTypes(context)).contains(List.class);
        assertThat(handlerOf(context, List.class)).isSameAs(handler);
    }

    @Test
    @DisplayName("同 javaType 绑定不同 handler 实例时启动失败（T18）")
    void sameCustomKeyDifferentHandlersFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                                List.of(new CustomTypeHandlerMapping(String.class, new MoneyHandler()))),
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                                List.of(new CustomTypeHandlerMapping(String.class, new MoneyHandler())))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("自定义 handler 冲突");
    }

    @Test
    @DisplayName("同 javaType 绑定同一 handler 实例时幂等通过（T19）")
    void sameCustomKeySameHandlerIsIdempotent() {
        MoneyHandler handler = new MoneyHandler();
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                                List.of(new CustomTypeHandlerMapping(String.class, handler))),
                        TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                                List.of(new CustomTypeHandlerMapping(String.class, handler)))),
                JdbcJsonValue.DEFAULT);

        assertThat(javaTypes(context).stream().filter(String.class::equals).count()).isEqualTo(1);
        assertThat(handlerOf(context, String.class)).isSameAs(handler);
    }

    @Test
    @DisplayName("handler 泛型与 javaType 无同型或子类型关系时启动失败（T20）")
    void genericMismatchFails() {
        assertThatThrownBy(() -> TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                        List.of(new CustomTypeHandlerMapping(Integer.class, new MoneyHandler())))),
                JdbcJsonValue.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("泛型与 javaType 不匹配");
    }

    @Test
    @DisplayName("raw 实现泛型不可解析时跳过校验放行（T21）")
    void rawHandlerSkipsGenericCheck() {
        RawHandler handler = new RawHandler();
        TypeHandlerContext context = TypeHandlerAssembler.assemble(
                List.of(TypeHandlerDeclaration.of(Map.of(), List.of(), List.of(),
                        List.of(new CustomTypeHandlerMapping(String.class, handler)))),
                JdbcJsonValue.DEFAULT);

        assertThat(handlerOf(context, String.class)).isSameAs(handler);
    }

    @Test
    @DisplayName("自定义配对的 javaType 或 handler 为 null 时构造期失败（T22）")
    void nullMappingPartsFailFast() {
        assertThatThrownBy(() -> new CustomTypeHandlerMapping(null, new MoneyHandler()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("javaType");
        assertThatThrownBy(() -> new CustomTypeHandlerMapping(String.class, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("三参 of 兼容：customHandlers 为空（T23）")
    void threeArgOfKeepsCustomHandlersEmpty() {
        TypeHandlerDeclaration declaration = TypeHandlerDeclaration.of(
                Map.of(Color.class, EnumRule.CODE), List.of(), List.of());

        assertThat(declaration.customHandlers()).isEmpty();
    }

    private List<Class<?>> javaTypes(TypeHandlerContext context) {
        return context.registrations().stream()
                .<Class<?>>map(TypeHandlerRegistration::javaType)
                .toList();
    }

    private TypeHandler<?> handlerOf(TypeHandlerContext context, Class<?> javaType) {
        return context.registrations().stream()
                .filter(registration -> registration.javaType() == javaType)
                .map(TypeHandlerRegistration::handler)
                .findFirst()
                .orElseThrow();
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
