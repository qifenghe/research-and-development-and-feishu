package com.lhr.rnd.api;

import com.lhr.rnd.model.FeishuNotification;
import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.service.FeishuDispatchResult;
import com.lhr.rnd.service.FeishuCardActionResult;
import com.lhr.rnd.service.FeishuIntegrationService;
import com.lhr.rnd.service.FeishuIntegrationStatus;
import com.lhr.rnd.service.FeishuLoginResult;
import com.lhr.rnd.service.SampleWorkflowService;
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
    private final SampleWorkflowService workflowService;

    public FeishuIntegrationController(
            FeishuIntegrationService feishuIntegrationService,
            SampleWorkflowService workflowService
    ) {
        this.feishuIntegrationService = feishuIntegrationService;
        this.workflowService = workflowService;
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

    @PostMapping("/oauth/callback")
    public ApiResponse<FeishuLoginResult> oauthCallback(@Valid @RequestBody FeishuOauthCallbackRequest request) {
        return ApiResponse.success(feishuIntegrationService.oauthCallback(
                new FeishuIntegrationService.OauthCallbackCommand(request.code())
        ));
    }

    @GetMapping("/integration/status")
    public ApiResponse<FeishuIntegrationStatus> integrationStatus() {
        return ApiResponse.success(feishuIntegrationService.integrationStatus());
    }

    @GetMapping("/notifications/pending")
    public ApiResponse<List<FeishuNotification>> pendingNotifications() {
        return ApiResponse.success(feishuIntegrationService.pendingNotifications());
    }

    @PostMapping("/notifications/dispatch")
    public ApiResponse<FeishuDispatchResult> dispatchPendingNotifications() {
        return ApiResponse.success(feishuIntegrationService.dispatchPendingNotifications());
    }

    @PostMapping("/card-actions")
    public ApiResponse<FeishuCardActionResult> cardAction(@Valid @RequestBody FeishuCardActionRequest request) {
        if (!"ACCEPT_RND_TASK".equals(request.action()) || !"RND_TASK".equals(request.businessType())) {
            throw new BusinessException("FEISHU_CARD_ACTION_UNSUPPORTED", "暂不支持该飞书卡片动作");
        }
        var user = feishuIntegrationService.activeUserByFeishuUserId(request.feishuUserId());
        var task = workflowService.acceptTask(request.businessId(), user.name());
        return ApiResponse.success(new FeishuCardActionResult(request.action(), "任务已接受", task));
    }
}
