package com.lhr.rnd.api;

import com.lhr.rnd.service.FeedbackDemoSeedService;
import com.lhr.rnd.service.MockDemoUserBootstrapService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/demo")
@Profile("local")
public class DemoWorkflowController {
    private final FeedbackDemoSeedService feedbackDemoSeedService;
    private final MockDemoUserBootstrapService mockDemoUserBootstrapService;

    public DemoWorkflowController(
            FeedbackDemoSeedService feedbackDemoSeedService,
            MockDemoUserBootstrapService mockDemoUserBootstrapService
    ) {
        this.feedbackDemoSeedService = feedbackDemoSeedService;
        this.mockDemoUserBootstrapService = mockDemoUserBootstrapService;
    }

    @PostMapping("/seed-feedback")
    public ApiResponse<FeedbackDemoSeedService.FeedbackDemoSeedResult> seedFeedbackDemo() {
        return ApiResponse.success(feedbackDemoSeedService.seedIfEmpty());
    }

    @PostMapping("/seed-users")
    public ApiResponse<List<MockDemoUserBootstrapService.DemoUserBinding>> seedDemoUsers() {
        return ApiResponse.success(mockDemoUserBootstrapService.seedDemoUsers());
    }
}
