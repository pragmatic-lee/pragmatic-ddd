package io.pragmatic.ddd.example.order.api.common;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 模拟登录上下文（对齐 order-backend-api-design.md 3.6 / 拍板 #7 方案 A）：
 * 当前以固定演示用户模拟登录态；后续接入真实登录态 / 网关透传后替换本实现。
 *
 * @author wizard-lee
 */
@Getter
@Component
public class MockLoginContext {

    /** 演示用户客户标识。 */
    private final long customerId = 1001L;

    /** 演示用户客户名称。 */
    private final String customerName = "张三";
}
