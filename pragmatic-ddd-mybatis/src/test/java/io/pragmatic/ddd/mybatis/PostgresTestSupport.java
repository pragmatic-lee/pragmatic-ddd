package io.pragmatic.ddd.mybatis;

import io.pragmatic.ddd.mybatis.compensation.CompensationMapper;
import io.pragmatic.ddd.mybatis.id.IdSegmentMapper;
import io.pragmatic.ddd.mybatis.outbox.OutboxMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

/**
 * 测试期 PostgreSQL 连接与 {@link SqlSessionFactory} 构建的通用支持类，
 * 与 {@link MysqlTestSupport} 完全对称，供 outbox / compensation / id-generator 等
 * 需要真实 PostgreSQL 的集成测试复用。
 *
 * <p>连接信息通过系统属性或环境变量覆盖，默认值如下（可按需覆盖）：
 * <ul>
 *   <li>{@code PG_HOST}       默认 127.0.0.1</li>
 *   <li>{@code PG_PORT}       默认 5432</li>
 *   <li>{@code PG_DB}         默认 pragmatic_ddd</li>
 *   <li>{@code PG_USER}       默认 pragmatic</li>
 *   <li>{@code PG_PASSWORD}   默认 PgXXL123</li>
 * </ul>
 *
 * <p>与 MySQL 版的关键差异：框架方言 XML 位于 {@code postgres/} 子目录（不在契约接口同包），
 * 无法走 {@code Configuration.addMapper} 的同包同名约定，须显式 {@link XMLMapperBuilder} 解析；
 * 同时必须 {@code configuration.setDatabaseId("postgres")}，否则方言语句全部不注册
 * （运行期首调 SQL 才抛 Mapped Statements not found）。</p>
 *
 * <p>无可达 PostgreSQL 时 {@link #isAvailable()} 返回 false，测试应配合 Assumptions 跳过。
 * 仅用于 test scope，postgresql 驱动不会进入框架产物。</p>
 *
 * @author wizard-lee
 */
public final class PostgresTestSupport {

    /** 契约接口 → jar 内方言 XML 路径（与 src/main/resources 保持一致）。 */
    private static final Map<Class<?>, String> DIALECT_XML = Map.of(
            OutboxMapper.class, "io/pragmatic/ddd/mybatis/outbox/postgres/OutboxMapper.xml",
            CompensationMapper.class, "io/pragmatic/ddd/mybatis/compensation/postgres/CompensationMapper.xml",
            IdSegmentMapper.class, "io/pragmatic/ddd/mybatis/id/postgres/IdSegmentMapper.xml");

    private PostgresTestSupport() {
    }

    /** 探测 PostgreSQL 是否可达且目标库存在；不可达或库不存在返回 false，供测试配合 Assumptions 跳过。 */
    public static boolean isAvailable() {
        try (Connection ignored = openConnection()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通用工厂：按传入的契约接口加载对应方言 XML，并返回 SqlSessionFactory。
     *
     * <p>库与表均已预先存在（建表脚本见各模块 {@code schema/*-postgres.sql}），
     * 本方法只负责连接与装配，不再执行任何建库 / 建表逻辑。</p>
     *
     * @param mappers 需要启用的模块契约（{@link OutboxMapper} / {@link CompensationMapper} / {@link IdSegmentMapper}）
     */
    public static SqlSessionFactory sessionFactory(Class<?>... mappers) throws Exception {
        return sessionFactory(cfg -> { }, mappers);
    }

    /**
     * 在 {@link #sessionFactory(Class[])} 基础上，允许调用方在方言 XML 解析前对
     * {@link Configuration} 做额外定制（如注册 TypeHandler 实例）。
     *
     * @param customizer 在方言 XML 解析前回调，用于注册 TypeHandler 等
     * @param mappers    需要启用的契约接口
     */
    public static SqlSessionFactory sessionFactory(java.util.function.Consumer<Configuration> customizer,
                                                    Class<?>... mappers) throws Exception {
        PooledDataSource ds = new PooledDataSource("org.postgresql.Driver", url(), user(), password());
        Configuration cfg = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        // 方言语句带 databaseId="postgres"，必须先设置 databaseId 再解析 XML，否则全部不注册
        cfg.setDatabaseId("postgres");
        customizer.accept(cfg);
        for (Class<?> mapper : mappers) {
            String resource = DIALECT_XML.get(mapper);
            if (resource != null) {
                // 框架方言契约：方言 XML 在 postgres/ 子目录，须显式解析；
                // parse() 内部会按 namespace 绑定契约接口，不会加载同包的 MySQL 版 XML
                try (InputStream in = Resources.getResourceAsStream(resource)) {
                    new XMLMapperBuilder(in, cfg, resource, cfg.getSqlFragments()).parse();
                }
            } else {
                // 非框架契约（业务 / 测试自有 Mapper）：走同包同名约定加载其 XML
                cfg.addMapper(mapper);
            }
        }
        return new SqlSessionFactoryBuilder().build(cfg);
    }

    private static Connection openConnection() throws Exception {
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setURL(url());
        ds.setUser(user());
        ds.setPassword(password());
        return ds.getConnection();
    }

    private static String url() {
        return "jdbc:postgresql://" + host() + ":" + port() + "/" + db();
    }

    private static String env(String key, String def) {
        Properties system = System.getProperties();
        String v = system.getProperty(key);
        if (v == null) {
            v = System.getenv(key);
        }
        return v == null ? def : v;
    }

    private static String host()     { return env("PG_HOST", "127.0.0.1"); }
    private static int    port()     { return Integer.parseInt(env("PG_PORT", "5432")); }
    private static String db()       { return env("PG_DB", "pragmatic_ddd"); }
    private static String user()     { return env("PG_USER", "pragmatic"); }
    private static String password() { return env("PG_PASSWORD", "PgXXL123"); }
}
