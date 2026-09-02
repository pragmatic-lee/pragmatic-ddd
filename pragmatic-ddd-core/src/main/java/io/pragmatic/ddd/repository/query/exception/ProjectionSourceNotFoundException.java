package io.pragmatic.ddd.repository.query.exception;

/**
 * 源未找到异常：指定的投影源未登记，或源下目标子投影未绑定裁剪器。
 * 语义上属于"接线/配置缺失"，应由集成模块在启动时确保登记，故归为不可重试。
 *
 * @author wizard-lee
 */
public class ProjectionSourceNotFoundException extends ProjectionException {

    public ProjectionSourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionSourceNotFoundException(String message) {
        super(message);
    }
}
