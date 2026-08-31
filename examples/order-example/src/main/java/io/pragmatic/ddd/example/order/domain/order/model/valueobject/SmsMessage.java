package io.pragmatic.ddd.example.order.domain.order.model.valueobject;

/**
 * 短信消息值对象：承载接收手机号与短信内容。
 *
 * @param mobile  接收手机号。
 * @param content 短信内容。
 * @author wizard-lee
 */
public record SmsMessage(String mobile, String content) {

}
