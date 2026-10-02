package io.pragmatic.ddd.application.compensation;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * 补偿包裹模板：把"成功即提交、异常即逆序补偿并重抛原异常"固化为一处实现。
 * 关闭范围的异常一律忽略并记录，避免其覆盖补偿结果与原始业务异常。
 *
 * @author wizard-lee
 */
public final class CompensationTemplate {

    private static final Logger log = Logger.getLogger(CompensationTemplate.class.getName());

    private CompensationTemplate() {
    }

    /**
     * 以默认选项执行无返回值的命令体。
     *
     * @param manager 补偿编排器
     * @param command 命令体
     */
    public static void run(ICompensationManager manager, Consumer<ICompensationScope> command) {
        run(manager.begin(), command);
    }

    /**
     * 在给定范围内执行无返回值的命令体。
     *
     * @param scope   补偿范围
     * @param command 命令体
     */
    public static void run(ICompensationScope scope, Consumer<ICompensationScope> command) {
        try {
            command.accept(scope);
            scope.commit();
            closeQuietly(scope);
        } catch (RuntimeException e) {
            try {
                scope.rollback(e);
            } finally {
                closeQuietly(scope);
            }
            throw e;
        }
    }

    /**
     * 以默认选项执行有返回值的命令体。
     *
     * @param manager 补偿编排器
     * @param command 命令体
     * @param <R>     返回值类型
     * @return 命令体返回值
     */
    public static <R> R call(ICompensationManager manager, Function<ICompensationScope, R> command) {
        return call(manager.begin(), command);
    }

    /**
     * 在给定范围内执行有返回值的命令体。
     *
     * @param scope   补偿范围
     * @param command 命令体
     * @param <R>     返回值类型
     * @return 命令体返回值
     */
    public static <R> R call(ICompensationScope scope, Function<ICompensationScope, R> command) {
        try {
            R result = command.apply(scope);
            scope.commit();
            closeQuietly(scope);
            return result;
        } catch (RuntimeException e) {
            try {
                scope.rollback(e);
            } finally {
                closeQuietly(scope);
            }
            throw e;
        }
    }

    private static void closeQuietly(ICompensationScope scope) {
        try {
            scope.close();
        } catch (RuntimeException e) {
            log.warning("compensation scope close failed: " + e.getMessage());
        }
    }
}
