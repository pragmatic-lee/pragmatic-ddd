package io.pragmatic.ddd.visual.entity;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字段读取器 —— 通过序列化 Lambda 解析 getter 对应的字段名、类型与归属类。
 *
 * @author wizard-lee
 */
public interface FieldGetter<T, R> extends Function<T, R>, Serializable {
    Pattern compile = Pattern.compile("\\(L(.+);\\)");

    /** 解析读取器对应字段的名称、类型与归属类信息。 */
    default FieldInfo getFieldName(String description, boolean collection, Class<?> collectionType) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
        Method method = this.getClass().getDeclaredMethod("writeReplace");
        method.setAccessible(true);
        SerializedLambda serializedLambda = (SerializedLambda) method.invoke(this);
        String methodName = serializedLambda.getImplMethodName();

        Matcher matcher = compile.matcher(serializedLambda.getInstantiatedMethodType());
        matcher.find();
        String classPath = matcher.group(1);


        String replace = classPath.replace('/', '.');

        Class<?> aClass = Class.forName(replace);


        String returnType;
        if (collection) {
            returnType = collectionType.getSimpleName();
        } else {
            int lastIndex = serializedLambda.getInstantiatedMethodType().lastIndexOf("/");
            returnType = serializedLambda.getInstantiatedMethodType().substring(lastIndex + 1,
                    serializedLambda.getInstantiatedMethodType().length() - 1);
        }


        if (methodName.startsWith("get")) {
            methodName = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            methodName = methodName.substring(2);
        }

        String fixMethodName = methodName.substring(0, 1).toLowerCase() + methodName.substring(1);

        return new FieldInfo(fixMethodName, description, returnType, aClass, collection);

    }
}
