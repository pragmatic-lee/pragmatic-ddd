package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.mybatis.typehandler.demo.ChannelEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.ColorEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.StatusEnum;
import io.pragmatic.ddd.mybatis.typehandler.demo.UserProfile;

import java.util.List;

/**
 * TypeHandler 真实库集成测试行对象，对应表 type_handler_demo。
 *
 * @author wizard-lee
 */
public class TypeHandlerDemoRow {
    private Long id;
    private String bizName;
    private StatusEnum statusCode;
    private ChannelEnum statusName;
    private UserProfile profileJson;
    private List<ColorEnum> colorsJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizName() {
        return bizName;
    }

    public void setBizName(String bizName) {
        this.bizName = bizName;
    }

    public StatusEnum getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(StatusEnum statusCode) {
        this.statusCode = statusCode;
    }

    public ChannelEnum getStatusName() {
        return statusName;
    }

    public void setStatusName(ChannelEnum statusName) {
        this.statusName = statusName;
    }

    public UserProfile getProfileJson() {
        return profileJson;
    }

    public void setProfileJson(UserProfile profileJson) {
        this.profileJson = profileJson;
    }

    public List<ColorEnum> getColorsJson() {
        return colorsJson;
    }

    public void setColorsJson(List<ColorEnum> colorsJson) {
        this.colorsJson = colorsJson;
    }
}
