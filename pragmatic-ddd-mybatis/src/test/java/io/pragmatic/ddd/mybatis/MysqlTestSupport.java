package io.pragmatic.ddd.mybatis;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.pragmatic.ddd.mybatis.outbox.MysqlOutboxMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 测试期 MySQL 连接与 {@link SqlSessionFactory} 构建支持。
 *
 * <p>仅用于 {@code test} scope（mysql-connector-j 不会进入框架产物）。连接信息通过
 * 系统属性 / 环境变量覆盖，默认值指向本地或 Docker 映射出的 3306：
 * <ul>
 *   <li>{@code MYSQL_HOST}   默认 {@code 127.0.0.1}</li>
 *   <li>{@code MYSQL_PORT}   默认 {@code 3306}</li>
 *   <li>{@code MYSQL_DB}     默认 {@code pragmatic_ddd_test}</li>
 *   <li>{@code MYSQL_USER}   默认 {@code root}</li>
 *   <li>{@code MYSQL_PASSWORD} 默认空</li>
 * </ul>
 * 每次构建都会重建 {@code outbox_message} 表，保证测试间相互隔离。</p>
 */
public final class MysqlTestSupport {

    private MysqlTestSupport() {
    }

    public static SqlSessionFactory sqlSessionFactory() throws Exception {
        MysqlDataSource serverDs = new MysqlDataSource();
        serverDs.setUrl(baseUrl());
        serverDs.setUser(user());
        serverDs.setPassword(password());
        try (Connection c = serverDs.getConnection();
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS " + db());
        }

        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(url());
        ds.setUser(user());
        ds.setPassword(password());

        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS outbox_message");
            s.execute(readSchema());
        }

        Configuration cfg = new Configuration();
        cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        cfg.addMapper(MysqlOutboxMapper.class);
        // 若后续测试用到自定义 type handler 的 mapper，在此统一注册
        return new SqlSessionFactoryBuilder().build(cfg);
    }

    private static String baseUrl() {
        return "jdbc:mysql://" + host() + ":" + port()
                + "/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
    }

    private static String url() {
        return "jdbc:mysql://" + host() + ":" + port() + "/" + db()
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
    }

    private static String readSchema() throws Exception {
        try (InputStream in = MysqlTestSupport.class.getResourceAsStream(
                "/io/pragmatic/ddd/mybatis/outbox/schema/outbox-schema-mysql.sql")) {
            if (in == null) {
                throw new IllegalStateException("outbox-schema-mysql.sql not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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
    private static String password() { return env("MYSQL_PASSWORD", "mysqlxxl123"); }
}
