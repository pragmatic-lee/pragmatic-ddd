package io.pragmatic.ddd.base;

public class CompareAndSetInfo<V> {
    private boolean isEqual;
    private V newValue;
    private V oldValue;

    public CompareAndSetInfo(boolean isEqual, V newValue, V oldValue) {


        this.isEqual = isEqual;
        this.newValue = newValue;
        this.oldValue = oldValue;
    }

    public V getOldValue() {
        return oldValue;
    }

    public V getNewValue() {
        return newValue;
    }

    public boolean isEqual() {
        return isEqual;
    }
}
