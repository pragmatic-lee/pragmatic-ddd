package io.pragmatic.ddd.repository.query.exception;

/**
 * 源未注册目标子投影裁剪器异常。
 * 语义上属于"接线/配置缺失"，区别于"数据不存在"（后者返回 null），故归为不可重试。
 *
 * @author wizard-lee
 */
public class ProjectionReducerNotFoundException extends ProjectionException {

    public ProjectionReducerNotFoundException(String message) {
        super(message);
    }

    public ProjectionReducerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
