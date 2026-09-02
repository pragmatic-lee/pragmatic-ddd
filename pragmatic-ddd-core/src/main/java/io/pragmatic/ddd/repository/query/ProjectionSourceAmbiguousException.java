package io.pragmatic.ddd.repository.query;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 源歧义异常：未指定源且目标子投影存在多个可用源、又未登记默认源时抛。
 * 异常信息列出全部可用源，便于调用方在 query 入口显式指定 source。
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
     * 由目标子投影类型与全部可用源构造异常信息。
     *
     * @param subProjection 目标子投影类型
     * @param sources 全部可用源
     * @return 歧义异常实例
     */
    public static ProjectionSourceAmbiguousException of(Class<?> subProjection, Collection<ProjectionSource> sources) {
        String ids = sources.stream()
                .map(ProjectionSource::id)
                .collect(Collectors.joining(", "));
        String message = "子投影 " + subProjection.getSimpleName() + " 存在多个可用源且无默认源，请显式指定来源：[" + ids + "]";
        return new ProjectionSourceAmbiguousException(message);
    }
}
