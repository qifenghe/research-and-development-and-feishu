package com.lhr.rnd.config;

import com.lhr.rnd.service.FeishuIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rnd.feishu.bootstrap-users", havingValue = "true")
public class LocalFeishuUserBootstrap {
    private static final Logger log = LoggerFactory.getLogger(LocalFeishuUserBootstrap.class);

    private final Environment environment;
    private final FeishuIntegrationService feishuIntegrationService;

    public LocalFeishuUserBootstrap(Environment environment, FeishuIntegrationService feishuIntegrationService) {
        this.environment = environment;
        this.feishuIntegrationService = feishuIntegrationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapUsers() {
        bindIfPresent("FEISHU_DEMO_ASSISTANT", "RND_ASSISTANT", "研发内勤");
        bindIfPresent("FEISHU_DEMO_DIRECTOR", "RND_DIRECTOR", "研发总监");
        bindIfPresent("FEISHU_DEMO_ENGINEER", "RND_ENGINEER", "研发人员");
        bindIfPresent("FEISHU_DEMO_FINANCE", "FINANCE", "财务");
    }

    private void bindIfPresent(String prefix, String role, String fallbackName) {
        var feishuUserId = environment.getProperty(prefix + "_USER_ID", "").trim();
        if (feishuUserId.isBlank() || feishuUserId.contains("xxx")) {
            return;
        }
        var name = environment.getProperty(prefix + "_NAME", fallbackName);
        feishuIntegrationService.bindUser(new FeishuIntegrationService.BindFeishuUserCommand(
                name,
                feishuUserId,
                role,
                "研发部"
        ));
        log.info("已自动绑定飞书用户：{} / {} / {}", name, maskFeishuUserId(feishuUserId), role);
    }

    private String maskFeishuUserId(String feishuUserId) {
        if (feishuUserId.length() <= 8) {
            return "****";
        }
        return feishuUserId.substring(0, 4) + "****" + feishuUserId.substring(feishuUserId.length() - 4);
    }
}
