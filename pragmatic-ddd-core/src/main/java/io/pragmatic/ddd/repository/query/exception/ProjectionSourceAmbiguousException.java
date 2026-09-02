package io.pragmatic.ddd.repository.query.exception;

import java.util.Collection;

/**
 * 源歧义异常：未指定源且目标子投影存在多个可用源、又未登记默认源时抛。
 * 异常信息列出全部可用源 id，便于调用方在 query 入口显式指定 source。
 *
 * @author wizard-lee
 */
public class ProjectionSourceAmbiguousException extends ProjectionException {

    public ProjectionSourceAmbiguousException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionSourceAmbiguousException(String message) {
        super(message);
    }

    /**
     * 由目标子投影类型与全部可用源 id 构造异常信息。
     *
     * @param subProjection 目标子投影类型
     * @param sourceIds 全部可用源 id
     * @return 歧义异常实例
     */
    public static ProjectionSourceAmbiguousException of(Class<?> subProjection, Collection<String> sourceIds) {
        String message = "子投影 " + subProjection.getSimpleName() + " 存在多个可用源且无默认源，请显式指定来源：["
                + String.join(", ", sourceIds) + "]";
        return new ProjectionSourceAmbiguousException(message);
    }
}
