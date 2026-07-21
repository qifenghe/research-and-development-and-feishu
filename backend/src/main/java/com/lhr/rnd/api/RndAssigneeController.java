package com.lhr.rnd.api;

import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.service.UserSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rnd-assignees")
public class RndAssigneeController {
    private final UserSettingsService userSettingsService;

    public RndAssigneeController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ApiResponse<List<UserAccount>> activeRndAssignees() {
        return ApiResponse.success(userSettingsService.activeRndAssignees());
    }
}
