package io.pragmatic.ddd.base;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractSubscriberKey {
    private final HashMap<String, KeySetting> keys;

    protected AbstractSubscriberKey() {
        this.keys = new HashMap<>();
        this.populateKeys();
    }

    protected abstract void populateKeys();

    public Map<String, KeySetting> getKeys() {
        return keys;
    }

    public KeySetting getKeyInfo(String key) {
        return keys.get(key);
    }

    public static KeySetting buildKeySetting(String description) {
        return buildKeySetting(false, description, null);
    }

    public static KeySetting buildKeySetting(String description, boolean mergeSameKey) {
        return buildKeySetting(mergeSameKey, description, null);
    }

    public static KeySetting buildKeySetting(boolean mergeSameKey, String description, Object customInfo) {
        return new KeySetting(mergeSameKey, description, customInfo);
    }

    public static class KeySetting {
        private boolean mergeSameKey;
        private String description;
        private Object customInfo;

        public KeySetting(boolean mergeSameKey, String description, Object customInfo) {
            this.mergeSameKey = mergeSameKey;
            this.description = description;
            this.customInfo = customInfo;
        }

        public boolean isMergeSameKey() {
            return mergeSameKey;
        }

        public void setMergeSameKey(boolean mergeSameKey) {
            this.mergeSameKey = mergeSameKey;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object getCustomInfo() {
            return customInfo;
        }

        public void setCustomInfo(Object customInfo) {
            this.customInfo = customInfo;
        }
    }
}
