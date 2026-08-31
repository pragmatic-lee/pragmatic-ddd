package io.pragmatic.ddd.example.order.infrastructure.order.dependency;

import io.pragmatic.ddd.example.order.domain.order.dependency.ISmsDependency;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.SmsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 短信服务防腐适配器：对接第三方短信平台（打桩实现）。
 *
 * @author wizard-lee
 */
@Component
public class SmsDependencyAdapter implements ISmsDependency {

    private static final Logger log = LoggerFactory.getLogger(SmsDependencyAdapter.class);

    @Override
    public void sendSms(SmsMessage message) {
        // 打桩：真实场景调用第三方短信平台（阿里云 / 腾讯云等）
        log.info("send sms to {}: {}", message.mobile(), message.content());
    }
}
