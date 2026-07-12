package com.lhr.rnd.config;

import com.lhr.rnd.service.DefaultWebAccountBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rnd.auth.bootstrap-users", havingValue = "true", matchIfMissing = true)
public class DefaultWebAccountBootstrap {
    private static final Logger log = LoggerFactory.getLogger(DefaultWebAccountBootstrap.class);

    private final DefaultWebAccountBootstrapService defaultWebAccountBootstrapService;

    public DefaultWebAccountBootstrap(DefaultWebAccountBootstrapService defaultWebAccountBootstrapService) {
        this.defaultWebAccountBootstrapService = defaultWebAccountBootstrapService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(50)
    public void bootstrapUsers() {
        var users = defaultWebAccountBootstrapService.seedDefaultAccounts();
        log.info("网页端默认账号已检查：{} 个账号可用，默认密码为 123456", users.size());
    }
}
