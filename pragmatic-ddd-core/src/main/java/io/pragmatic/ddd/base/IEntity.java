package io.pragmatic.ddd.base;


/**
 * 实体标识接口，约束实体暴露其标识（ID）。
 *
 * @param <T> 标识类型
 * @author wizard-lee
 */
public interface IEntity<T> {
	/** 返回实体标识。 */
	T getEntityId();
}
