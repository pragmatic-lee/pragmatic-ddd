package io.pragmatic.ddd.mybatis.typehandler.demo;

/**
 * 演示值对象：对应 JSON 列 profile_json，内嵌枚举字段 status（CODE 策略）。
 *
 * @author wizard-lee
 */
public class UserProfile {
    private String nickname;
    private StatusEnum status;
    private int level;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public StatusEnum getStatus() {
        return status;
    }

    public void setStatus(StatusEnum status) {
        this.status = status;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}
