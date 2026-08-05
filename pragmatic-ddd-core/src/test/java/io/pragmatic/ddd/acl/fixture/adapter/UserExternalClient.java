package io.pragmatic.ddd.acl.fixture.adapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟的对方 User 系统客户端（仅存在于防腐层）。生产环境替换为真实 RPC / HTTP 客户端。
 *
 * @author wizard-lee
 */
public class UserExternalClient {

    private final Map<String, UserLevelResp> store = new ConcurrentHashMap<>();

    public UserExternalClient() {
        store.put("u1", new UserLevelResp(3));
    }

    public boolean exists(String userId) {
        return store.containsKey(userId);
    }

    public int level(String userId) {
        UserLevelResp resp = store.get(userId);
        return resp == null ? 0 : resp.level();
    }

    public record UserExistsReq(String userId) {
    }

    public record UserExistsResp(boolean exist) {
    }

    public record UserLevelReq(String userId) {
    }

    public record UserLevelResp(int level) {
    }
}
