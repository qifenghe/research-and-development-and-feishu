package com.lhr.rnd.api;

import com.lhr.rnd.model.UserAccount;
import com.lhr.rnd.service.UserSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/users")
public class UserSettingsController {
    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ApiResponse<List<UserAccount>> users() {
        return ApiResponse.success(userSettingsService.users());
    }

    @PutMapping("/{feishuUserId}")
    public ApiResponse<UserAccount> saveUser(
            @PathVariable String feishuUserId,
            @Valid @RequestBody SaveUserSettingsRequest request
    ) {
        return ApiResponse.success(userSettingsService.saveUser(new UserSettingsService.SaveUserCommand(
                feishuUserId,
                request.name(),
                request.role(),
                request.departmentName()
        )));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<UserAccount> enableUser(@PathVariable String id) {
        return ApiResponse.success(userSettingsService.enableUser(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<UserAccount> disableUser(@PathVariable String id) {
        return ApiResponse.success(userSettingsService.disableUser(id));
    }
}
