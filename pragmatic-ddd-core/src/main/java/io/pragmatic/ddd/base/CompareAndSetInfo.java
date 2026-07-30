package io.pragmatic.ddd.base;

/**
 * CAS 比较结果封装：是否相等，以及比较的新值与旧值。
 *
 * @param <V> 值类型
 * @author wizard-lee
 */
public class CompareAndSetInfo<V> {
    private boolean isEqual;
    private V newValue;
    private V oldValue;

    /** 以比较结果与新/旧值构造。 */
    public CompareAndSetInfo(boolean isEqual, V newValue, V oldValue) {


        this.isEqual = isEqual;
        this.newValue = newValue;
        this.oldValue = oldValue;
    }

    /** 返回旧值。 */
    public V getOldValue() {
        return oldValue;
    }

    /** 返回新值。 */
    public V getNewValue() {
        return newValue;
    }

    /** 返回比较结果是否相等。 */
    public boolean isEqual() {
        return isEqual;
    }
}
