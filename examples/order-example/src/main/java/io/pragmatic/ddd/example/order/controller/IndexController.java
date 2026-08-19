package io.pragmatic.ddd.example.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 示例首版启动测试控制器，用于验证应用可正常启动并响应请求。
 *
 * @author wizard-lee
 */
@RestController
public class IndexController {

    @GetMapping("/")
    public String index() {
        return "Pragmatic DDD Order Example is running.";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
