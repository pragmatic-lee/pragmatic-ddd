package io.pragmatic.ddd.mybatis;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.pragmatic.ddd.mybatis.id.IdSegmentMapper;
import io.pragmatic.ddd.mybatis.outbox.OutboxMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.sql.Connection;

/**
 * 测试期 MySQL 连接与 {@link SqlSessionFactory} 构建的通用支持类，
 * 供 outbox / id-generator 等需要真实 MySQL 的集成测试复用。
 *
 * <p>连接信息通过系统属性或环境变量覆盖，默认值如下（可按需覆盖）：
 * <ul>
 *   <li>{@code MYSQL_HOST}       默认 127.0.0.1</li>
 *   <li>{@code MYSQL_PORT}       默认 3306</li>
 *   <li>{@code MYSQL_DB}         默认 pragmatic_ddb</li>
 *   <li>{@code MYSQL_USER}       默认 root</li>
 *   <li>{@code MYSQL_PASSWORD}   默认 mysqlxxl123</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   &#64;BeforeAll static void init() {
 *       Assumptions.assumeTrue(MysqlTestSupport.isAvailable(), "MySQL 不可用，跳过");
 *       ssf = MysqlTestSupport.sessionFactory(XxxMapper.class);
 *   }
 * </pre>
 *
 * <p>装配在 {@link #sessionFactory(Class[])} 内完成：根据传入的契约接口（{@link IdSegmentMapper} /
 * {@link OutboxMapper}）调用 {@code Configuration.addMapper} 注册，MyBatis 随即按同包同名规则
 * 自动从 classpath 加载对应 XML（如 {@code OutboxMapper.xml}）。模块保持 Spring 无关。</p>
 *
 * <p>无可达 MySQL 时 {@link #isAvailable()} 返回 false，测试应跳过以免构建失败。
 * 仅用于 test scope，mysql-connector-j 不会进入框架产物。</p>
 */
public final class MysqlTestSupport {

    private MysqlTestSupport() {
    }

    /** 探测 MySQL 是否可达且目标库存在；不可达或库不存在返回 false，供测试配合 Assumptions 跳过。 */
    public static boolean isAvailable() {
        try (Connection ignored = openConnection(url())) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通用工厂：注册 mapper 契约接口并返回 SqlSessionFactory。
     *
     * <p>默认库与表均已预先存在，本方法只负责连接与装配，不再执行任何建库 / 建表逻辑。</p>
     *
     * @param mappers 需要启用的模块契约（{@link IdSegmentMapper} / {@link OutboxMapper}），
     *                据此决定模块开关
     */
    public static SqlSessionFactory sessionFactory(Class<?>... mappers) throws Exception {
        // 库与表均已存在，直接构建 SqlSessionFactory 并装配：
        // 注入表名变量 + 注册 mapper 契约接口（MyBatis 按同包同名规则自动加载对应 XML）
        return sessionFactory(cfg -> { }, mappers);
    }

    /**
     * 在 {@link #sessionFactory(Class[])} 基础上，允许调用方在 mapper 注册前对
     * {@link Configuration} 做额外定制（如注册带参构造的 TypeHandler 实例）。
     *
     * @param customizer 在 mapper 注册前回调，用于注册 TypeHandler 等
     * @param mappers    需要启用的契约接口
     */
    public static SqlSessionFactory sessionFactory(java.util.function.Consumer<Configuration> customizer,
                                                    Class<?>... mappers) throws Exception {
        PooledDataSource ds = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver", url(), user(), password());
        Configuration cfg = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        customizer.accept(cfg);
        for (Class<?> mapper : mappers) {
            cfg.addMapper(mapper);
        }
        return new SqlSessionFactoryBuilder().build(cfg);
    }

    /** outbox 测试便捷方法：装配契约接口 OutboxMapper。 */
    public static SqlSessionFactory sqlSessionFactory() throws Exception {
        return sessionFactory(OutboxMapper.class);
    }

    private static Connection openConnection(String jdbcUrl) throws Exception {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUser(user());
        ds.setPassword(password());
        return ds.getConnection();
    }

    private static String url() {
        return "jdbc:mysql://" + host() + ":" + port() + "/" + db()
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    }

    private static String env(String key, String def) {
        String v = System.getProperty(key);
        if (v == null) {
            v = System.getenv(key);
        }
        return v == null ? def : v;
    }

    private static String host()     { return env("MYSQL_HOST", "127.0.0.1"); }
    private static int    port()     { return Integer.parseInt(env("MYSQL_PORT", "3306")); }
    private static String db()       { return env("MYSQL_DB", "pragmatic_ddb"); }
    private static String user()     { return env("MYSQL_USER", "root"); }
    private static String password() { return env("MYSQL_PASSWORD", "MySqlXXL123"); }
}
