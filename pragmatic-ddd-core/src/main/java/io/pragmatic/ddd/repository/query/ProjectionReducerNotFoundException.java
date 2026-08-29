package io.pragmatic.ddd.repository.query;

/**
 * 裁剪器未找到异常：注册表中不存在 (sourceType, projectionType) 对应的 reducer，
 * 或子投影未登记来源、无法反查索引级全量投影。
 * 语义上属于"接线/配置缺失"，应由集成模块在启动时确保登记，故归为不可重试。
 *
 * @author wizard-lee
 */
public class ProjectionReducerNotFoundException extends ProjectionException {

    public ProjectionReducerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionReducerNotFoundException(String message) {
        super(message);
    }
}
