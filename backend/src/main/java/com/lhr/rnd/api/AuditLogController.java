package com.lhr.rnd.api;

import com.lhr.rnd.persistence.entity.AuditLogEntity;
import com.lhr.rnd.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<List<AuditLogEntity>> recentAuditLogs() {
        return ApiResponse.success(auditLogService.recent());
    }
}
