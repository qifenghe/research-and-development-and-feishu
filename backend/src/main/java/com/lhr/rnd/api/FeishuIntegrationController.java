package com.lhr.rnd.api;

import com.lhr.rnd.model.FeishuNotification;
import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.service.FeishuDispatchResult;
import com.lhr.rnd.service.FeishuIntegrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feishu")
public class FeishuIntegrationController {
    private final FeishuIntegrationService feishuIntegrationService;

    public FeishuIntegrationController(FeishuIntegrationService feishuIntegrationService) {
        this.feishuIntegrationService = feishuIntegrationService;
    }

    @PostMapping("/users/bind")
    public ApiResponse<UserAccount> bindUser(@Valid @RequestBody BindFeishuUserRequest request) {
        return ApiResponse.success(feishuIntegrationService.bindUser(new FeishuIntegrationService.BindFeishuUserCommand(
                request.name(),
                request.feishuUserId(),
                request.role(),
                request.departmentName()
        )));
    }

    @GetMapping("/notifications/pending")
    public ApiResponse<List<FeishuNotification>> pendingNotifications() {
        return ApiResponse.success(feishuIntegrationService.pendingNotifications());
    }

    @PostMapping("/notifications/dispatch")
    public ApiResponse<FeishuDispatchResult> dispatchPendingNotifications() {
        return ApiResponse.success(feishuIntegrationService.dispatchPendingNotifications());
    }
}
