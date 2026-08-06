package io.pragmatic.ddd.acl;

import java.util.function.Supplier;

/**
 * ACL 调用过程的异常包装辅助，收敛转换/通信两类异常的样板处理。
 * 统一保证：触发 onError（原始异常）、包装为对应的 AclException 并保留 cause；
 * 若步骤已主动抛出 AclException，则原样传递，避免因果嵌套。
 *
 * @author wizard-lee
 */
public final class AclExceptions {

    private AclExceptions() {
    }

    /**
     * 本地转换步骤包装：转换失败归为 {@link AclConversionException}（不可重试）。
     *
     * @param step   转换步骤
     * @param stage  阶段描述（用于异常文案）
     * @param logger 日志钩子
     * @param <T>    步骤返回值类型
     * @return 步骤返回值
     */
    public static <T> T convert(Supplier<T> step, String stage, ExternalCallLogger<?, ?> logger) {
        try {
            return step.get();
        } catch (AclException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            logger.onError(ex);
            throw new AclConversionException("ACL " + stage + "失败", ex);
        }
    }

    /**
     * 外部调用步骤包装：通信失败归为 {@link AclCommunicationException}（通常可重试）。
     *
     * @param step   调用步骤
     * @param stage  阶段描述（用于异常文案）
     * @param logger 日志钩子
     * @param <T>    步骤返回值类型
     * @return 步骤返回值
     */
    public static <T> T communicate(Supplier<T> step, String stage, ExternalCallLogger<?, ?> logger) {
        try {
            return step.get();
        } catch (AclException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            logger.onError(ex);
            throw new AclCommunicationException("ACL " + stage + "失败", ex);
        }
    }
}
