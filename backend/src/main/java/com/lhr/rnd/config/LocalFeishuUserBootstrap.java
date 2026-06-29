package com.lhr.rnd.config;

import com.lhr.rnd.service.MockDemoUserBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rnd.feishu.bootstrap-users", havingValue = "true")
public class LocalFeishuUserBootstrap {
    private static final Logger log = LoggerFactory.getLogger(LocalFeishuUserBootstrap.class);

    private final MockDemoUserBootstrapService mockDemoUserBootstrapService;

    public LocalFeishuUserBootstrap(MockDemoUserBootstrapService mockDemoUserBootstrapService) {
        this.mockDemoUserBootstrapService = mockDemoUserBootstrapService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapUsers() {
        var bindings = mockDemoUserBootstrapService.seedDemoUsers();
        log.info("已自动绑定 {} 个 MOCK 演示账号", bindings.size());
        bindings.forEach(binding -> log.info(
                "MOCK 用户：{} / {} / {}",
                binding.name(),
                maskFeishuUserId(binding.feishuUserId()),
                binding.role()
        ));
    }

    private String maskFeishuUserId(String feishuUserId) {
        if (feishuUserId.length() <= 8) {
            return "****";
        }
        return feishuUserId.substring(0, 4) + "****" + feishuUserId.substring(feishuUserId.length() - 4);
    }
}
