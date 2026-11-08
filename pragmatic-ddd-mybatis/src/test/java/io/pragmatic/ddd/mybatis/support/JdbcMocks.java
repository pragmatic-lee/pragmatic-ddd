package io.pragmatic.ddd.mybatis.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        List<String> labels = List.copyOf(columns.keySet());
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
                case "getMetaData" -> {
                    return fakeResultSetMetaData(labels);
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

    /** 列信息元数据假对象：列数取自标签列表，列标签按序号返回，供按序号读取的 handler 反查标签。 */
    public static ResultSetMetaData fakeResultSetMetaData(List<String> labels) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getColumnCount" -> {
                    return labels.size();
                }
                case "getColumnLabel", "getColumnName" -> {
                    return labels.get((Integer) args[0] - 1);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "FakeResultSetMetaData";
                }
                default -> throw new UnsupportedOperationException("FakeResultSetMetaData: " + method);
            }
        };
        return (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class},
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

    /** 按序号 + 显式列标签构造：序号路径可通过元数据反查到该标签。 */
    public static ResultSet fakeResultSetWithIndexLabel(int index, String label, Object value, boolean wasNull) {
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put(label, value);
        Map<Integer, Object> indexes = new HashMap<>();
        indexes.put(index, value);
        return fakeResultSet(columns, indexes, wasNull);
    }
}
