package io.pragmatic.ddd.mybatis.compensation;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 补偿日志表 Mapper 契约接口（可选，与具体数据库无关的契约定义）。
 *
 * <p>仅声明方法签名，不含任何 SQL；具体 SQL 由同包同名 {@code CompensationMapper.xml} 提供（当前为 MySQL）。
 * 框架默认走传统纯 XML 直调方式（MybatisCompensationLog 按 namespace.statementId 直接调用），使用方无需引入本接口；
 * 若偏好接口式仍可 addMapper + getMapper，与纯 XML 扫描不冲突。模块保持 Spring 无关。</p>
 *
 * @author wizard-lee
 */
public interface CompensationMapper {

    /** 正向执行前登记（PENDING）。由调用方事务包裹，本方法自身不开启事务。 */
    int insert(@Param("record") CompensationRecord record);

    /** PENDING → EXECUTED（正向成功）。 */
    int markExecuted(@Param("actionKey") String actionKey);

    /** EXECUTED → COMPENSATING（原子认领）。返回受影响行数，0 表示已被其他实例认领。 */
    int markCompensating(@Param("actionKey") String actionKey, @Param("claimToken") String claimToken);

    /** EXECUTED / COMPENSATING → COMPENSATED（补偿完成）。 */
    int markCompensated(@Param("actionKey") String actionKey);

    /** → FAILED，累加尝试次数并记录原因。 */
    int markFailed(@Param("actionKey") String actionKey, @Param("reason") String reason);

    /** 待补偿记录（status = EXECUTED）。 */
    List<CompensationRecord> findExecuted(@Param("limit") int limit);

    /** 悬挂记录（status = PENDING，正向结果未知，不得自动补偿）。 */
    List<CompensationRecord> findSuspended(@Param("limit") int limit);

    /** 补偿失败记录（status = FAILED）。 */
    List<CompensationRecord> findFailed(@Param("limit") int limit);
}
