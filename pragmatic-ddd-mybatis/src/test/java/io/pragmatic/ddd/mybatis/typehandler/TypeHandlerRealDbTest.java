package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.mybatis.MysqlTestSupport;
import io.pragmatic.ddd.mybatis.typehandler.demo.ColorEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.ChannelEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.StatusEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.UserProfile;
import io.pragmatic.ddd.mybatis.typehandler.enums.DefaultEnumCodec;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.enums.UniversalEnumTypeHandler;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.GenericJsonTypeHandler;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionElementTypeConfig;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionMapping;
import io.pragmatic.ddd.mybatis.typehandler.list.ListTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TypeHandler 真实 MySQL 集成测试：验证单列枚举、JSON 值对象、枚举 JSON List 三类通道的端到端读写。
 *
 * <p>表 type_handler_demo 由人工创建（见 src/test/resources 下 schema SQL）。无可达 MySQL 时整类跳过。</p>
 *
 * @author wizard-lee
 */
@DisplayName("TypeHandler 真实库集成测试")
class TypeHandlerRealDbTest {

    private static SqlSessionFactory ssf;

    @BeforeAll
    static void init() throws Exception {
        Assumptions.assumeTrue(MysqlTestSupport.isAvailable(), "MySQL 不可用，跳过真实库测试");
        ssf = MysqlTestSupport.sessionFactory(TypeHandlerRealDbTest::configure, TypeHandlerDemoMapper.class);
    }

    @AfterAll
    static void close() {
        if (ssf != null) {
            ssf.openSession().close();
        }
    }

    @BeforeEach
    void resetTable() {
        if (ssf == null) {
            return;
        }
        try (SqlSession session = ssf.openSession(true)) {
            session.getConnection().prepareStatement("TRUNCATE TABLE type_handler_demo").executeUpdate();
        } catch (Exception ignored) {
            // 表尚未创建时跳过，交由用例自身失败暴露
        }
    }

    private static void truncate(SqlSession session) {
        try {
            session.getConnection().prepareStatement("TRUNCATE TABLE type_handler_demo").executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void configure(Configuration cfg) {
        EnumValueResolver resolver = new EnumValueResolver(new DefaultEnumCodec());
        resolver.registerAll(Map.of(StatusEnum.class, EnumRule.CODE, ColorEnum.class, EnumRule.CODE));

        // 按 javaType 注册带参构造的 handler 实例，XML 不指定 typeHandler 属性，由 MyBatis 按 javaType 自动匹配
        cfg.getTypeHandlerRegistry().register(StatusEnum.class,
                new UniversalEnumTypeHandler(StatusEnum.class, EnumRule.CODE, resolver));
        cfg.getTypeHandlerRegistry().register(ChannelEnum.class,
                new UniversalEnumTypeHandler(ChannelEnum.class, EnumRule.NAME, resolver));

        GenericJsonTypeHandler<UserProfile> profileHandler = new GenericJsonTypeHandler<>(
                UserProfile.class, new Fastjson2JsonSerializer(resolver, Map.of()), JdbcJsonValue.MYSQL);
        cfg.getTypeHandlerRegistry().register(UserProfile.class, profileHandler);

        CollectionElementTypeConfig listConfig = CollectionElementTypeConfig.from(
                List.of(CollectionMapping.of(TypeHandlerDemoRow.class, "colorsJson", ColorEnum.class)
                        .columnLabel("colors_json").build()),
                resolver);
        ListTypeHandler listHandler = new ListTypeHandler(
                new Fastjson2JsonSerializer(resolver, Map.of()),
                JdbcJsonValue.MYSQL,
                listConfig.columnListTypes(),
                listConfig.converters(),
                listConfig.columnListTypes().get("colors_json"));
        cfg.getTypeHandlerRegistry().register(List.class, listHandler);
    }

    private TypeHandlerDemoRow buildSample() {
        TypeHandlerDemoRow row = new TypeHandlerDemoRow();
        row.setBizName("demo-1");
        row.setStatusCode(StatusEnum.ACTIVE);
        row.setStatusName(ChannelEnum.APP);
        UserProfile profile = new UserProfile();
        profile.setNickname("alice");
        profile.setStatus(StatusEnum.ARCHIVED);
        profile.setLevel(5);
        row.setProfileJson(profile);
        row.setColorsJson(List.of(ColorEnum.RED, ColorEnum.GREEN, ColorEnum.BLUE));
        return row;
    }

    @Test
    @DisplayName("单列枚举 / JSON VO / 枚举 JSON List 端到端往返")
    void fullRoundtrip() {
        try (SqlSession session = ssf.openSession(true)) {
            TypeHandlerDemoMapper mapper = session.getMapper(TypeHandlerDemoMapper.class);

            TypeHandlerDemoRow sample = buildSample();
            mapper.insert(sample);

            TypeHandlerDemoRow loaded = mapper.selectById(1L);

            assertThat(loaded.getBizName()).isEqualTo("demo-1");
            assertThat(loaded.getStatusCode()).isEqualTo(StatusEnum.ACTIVE);
            assertThat(loaded.getStatusName()).isEqualTo(ChannelEnum.APP);
            assertThat(loaded.getProfileJson().getNickname()).isEqualTo("alice");
            assertThat(loaded.getProfileJson().getStatus()).isEqualTo(StatusEnum.ARCHIVED);
            assertThat(loaded.getProfileJson().getLevel()).isEqualTo(5);
            assertThat(loaded.getColorsJson()).containsExactly(ColorEnum.RED, ColorEnum.GREEN, ColorEnum.BLUE);
        }
    }

    @Test
    @DisplayName("枚举列 NULL 往返")
    void enumNullRoundtrip() {
        try (SqlSession session = ssf.openSession(true)) {
            TypeHandlerDemoMapper mapper = session.getMapper(TypeHandlerDemoMapper.class);

            TypeHandlerDemoRow row = new TypeHandlerDemoRow();
            row.setBizName("null-enum");
            row.setStatusCode(null);
            row.setStatusName(null);
            mapper.insert(row);

            TypeHandlerDemoRow loaded = mapper.selectById(1L);

            assertThat(loaded.getStatusCode()).isNull();
            assertThat(loaded.getStatusName()).isNull();
        }
    }

    @Test
    @DisplayName("JSON VO 与枚举 JSON List 的 NULL 往返")
    void jsonNullRoundtrip() {
        try (SqlSession session = ssf.openSession(true)) {
            TypeHandlerDemoMapper mapper = session.getMapper(TypeHandlerDemoMapper.class);

            TypeHandlerDemoRow row = new TypeHandlerDemoRow();
            row.setBizName("null-json");
            row.setProfileJson(null);
            row.setColorsJson(null);
            mapper.insert(row);

            TypeHandlerDemoRow loaded = mapper.selectById(1L);

            assertThat(loaded.getProfileJson()).isNull();
            assertThat(loaded.getColorsJson()).isNull();
        }
    }

    @Test
    @DisplayName("空枚举 JSON List 往返")
    void emptyListRoundtrip() {
        try (SqlSession session = ssf.openSession(true)) {
            TypeHandlerDemoMapper mapper = session.getMapper(TypeHandlerDemoMapper.class);

            TypeHandlerDemoRow row = buildSample();
            row.setColorsJson(List.of());
            mapper.insert(row);

            TypeHandlerDemoRow loaded = mapper.selectById(1L);

            assertThat(loaded.getColorsJson()).isEmpty();
        }
    }

    @Test
    @DisplayName("读回不存在的枚举 code 时抛异常")
    void unknownEnumCodeThrows() {
        try (SqlSession session = ssf.openSession(true)) {
            TypeHandlerDemoMapper mapper = session.getMapper(TypeHandlerDemoMapper.class);

            TypeHandlerDemoRow row = new TypeHandlerDemoRow();
            row.setBizName("bad-code");
            row.setStatusCode(null);
            row.setStatusName(null);
            mapper.insert(row);

            // 直接写入数据库一个不存在的 code，绕过 handler 序列化，制造脏数据
            session.getConnection().prepareStatement(
                    "UPDATE type_handler_demo SET status_code = 99 WHERE id = 1").executeUpdate();

            org.junit.jupiter.api.Assertions.assertThrows(
                    org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> mapper.selectById(1L));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
