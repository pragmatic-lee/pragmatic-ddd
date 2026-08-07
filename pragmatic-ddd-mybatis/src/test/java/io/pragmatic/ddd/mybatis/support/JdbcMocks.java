package io.pragmatic.ddd.mybatis.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于动态代理的轻量 JDBC 假对象工厂，仅实现 typehandler 测试真正用到的 set/get 方法，
 * 其余接口方法统一抛出 UnsupportedOperationException。天然适配任意 JDK 版本的 java.sql 接口。
 *
 * @author wizard-lee
 */
public final class JdbcMocks {

    private JdbcMocks() {
    }

    /** PreparedStatement 调用记录器，供断言使用。 */
    public static final class PsRecorder {
        final Map<Integer, Object> objects = new HashMap<>();
        final Map<Integer, String> strings = new HashMap<>();
        final Map<Integer, Integer> nulls = new HashMap<>();

        public Object objectParam(int index) {
            return objects.get(index);
        }

        public String stringParam(int index) {
            return strings.get(index);
        }

        public Integer nullType(int index) {
            return nulls.get(index);
        }
    }

    public static PreparedStatement fakePreparedStatement(PsRecorder recorder) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "setObject" -> {
                    recorder.objects.put((Integer) args[0], args[1]);
                    return null;
                }
                case "setString" -> {
                    recorder.strings.put((Integer) args[0], (String) args[1]);
                    return null;
                }
                case "setNull" -> {
                    recorder.nulls.put((Integer) args[0], (Integer) args[1]);
                    return null;
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "FakePreparedStatement";
                }
                default -> throw new UnsupportedOperationException("FakePreparedStatement: " + method);
            }
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                handler);
    }

    public static ResultSet fakeResultSet(Map<String, Object> columns, Map<Integer, Object> indexes, boolean wasNull) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getObject" -> {
                    if (args[0] instanceof String label) {
                        return columns.get(label);
                    }
                    return indexes.get((Integer) args[0]);
                }
                case "wasNull" -> {
                    return wasNull;
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "FakeResultSet";
                }
                default -> throw new UnsupportedOperationException("FakeResultSet: " + method);
            }
        };
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                handler);
    }

    public static ResultSet fakeResultSetWithColumn(String column, Object value, boolean wasNull) {
        Map<String, Object> columns = new HashMap<>();
        columns.put(column, value);
        return fakeResultSet(columns, new HashMap<>(), wasNull);
    }

    public static ResultSet fakeResultSetWithIndex(int index, Object value, boolean wasNull) {
        Map<Integer, Object> indexes = new HashMap<>();
        indexes.put(index, value);
        return fakeResultSet(new HashMap<>(), indexes, wasNull);
    }
}
