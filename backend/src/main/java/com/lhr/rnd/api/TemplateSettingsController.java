package com.lhr.rnd.api;

import com.lhr.rnd.model.TemplateConfigItem;
import com.lhr.rnd.service.TemplateSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/templates")
public class TemplateSettingsController {
    private final TemplateSettingsService templateSettingsService;

    public TemplateSettingsController(TemplateSettingsService templateSettingsService) {
        this.templateSettingsService = templateSettingsService;
    }

    @GetMapping
    public ApiResponse<List<TemplateConfigItem>> listTemplates() {
        return ApiResponse.success(templateSettingsService.listAll());
    }

    @GetMapping("/type/{templateType}")
    public ApiResponse<List<TemplateConfigItem>> listByType(@PathVariable String templateType) {
        return ApiResponse.success(templateSettingsService.listByType(templateType));
    }

    @PutMapping("/{templateCode}")
    public ApiResponse<TemplateConfigItem> updateTemplate(
            @PathVariable String templateCode,
            @Valid @RequestBody UpdateTemplateRequest request
    ) {
        return ApiResponse.success(templateSettingsService.update(
                templateCode,
                request.templateName(),
                request.filePath(),
                request.versionNo(),
                request.status()
        ));
    }

    public record UpdateTemplateRequest(
            @NotBlank String templateName,
            String filePath,
            @NotBlank String versionNo,
            @NotBlank String status
    ) {
    }
}
