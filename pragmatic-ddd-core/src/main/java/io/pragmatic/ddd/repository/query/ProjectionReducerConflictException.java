package io.pragmatic.ddd.repository.query;

/**
 * 裁剪器来源冲突异常：同一子投影被登记为可从多个不同的索引级全量投影裁剪而来，
 * 导致按子投影反查来源时存在歧义、门面无法确定该查哪个索引。
 *
 * <p>本异常在登记期（装配 registry 时）抛出，使"同一子投影多来源"的接线错误
 * 在启动阶段即暴露，而不是延迟到首次查询时静默选错索引。</p>
 *
 * @author wizard-lee
 */
public class ProjectionReducerConflictException extends ProjectionException {

    public ProjectionReducerConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionReducerConflictException(String message) {
        super(message);
    }
}
