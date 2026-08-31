package io.pragmatic.ddd.example.order.domain.order.dependency;

import io.pragmatic.ddd.dependency.DependencyType;
import io.pragmatic.ddd.dependency.ExternalDependency;
import io.pragmatic.ddd.dependency.IDependency;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.SmsMessage;

/**
 * 订单聚合对短信服务的外部依赖声明：向用户手机号发送短信。
 * 仅声明"发短信"这一能力，不感知第三方平台与发送细节（依赖倒置）。
 *
 * @author wizard-lee
 */
@ExternalDependency(
        targetName = "SmsService",
        type = DependencyType.EXTERNAL_SYSTEM,
        description = "短信服务：向用户手机号发送短信通知")
public interface ISmsDependency extends IDependency {

    /** 发送一条短信。 */
    void sendSms(SmsMessage message);
}
