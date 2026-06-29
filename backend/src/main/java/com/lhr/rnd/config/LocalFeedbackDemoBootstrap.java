package com.lhr.rnd.config;

import com.lhr.rnd.service.FeedbackDemoSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rnd.feedback.bootstrap-demo", havingValue = "true", matchIfMissing = true)
public class LocalFeedbackDemoBootstrap {
    private static final Logger log = LoggerFactory.getLogger(LocalFeedbackDemoBootstrap.class);

    private final FeedbackDemoSeedService feedbackDemoSeedService;

    public LocalFeedbackDemoBootstrap(FeedbackDemoSeedService feedbackDemoSeedService) {
        this.feedbackDemoSeedService = feedbackDemoSeedService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(200)
    public void bootstrapFeedbackDemo() {
        var result = feedbackDemoSeedService.seedIfEmpty();
        log.info("寄样反馈演示数据：待登记 {} 条，待反馈 {} 条 — {}", 
                result.pendingShipmentCount(), 
                result.pendingFeedbackCount(), 
                result.message());
    }
}
