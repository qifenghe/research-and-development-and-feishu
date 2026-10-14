package com.lhr.rnd.api;

import com.lhr.rnd.service.ReportExportFile;
import com.lhr.rnd.service.ReportExportService;
import com.lhr.rnd.service.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportExportController {
    private final ReportExportService reportExportService;

    public ReportExportController(ReportExportService reportExportService) {
        this.reportExportService = reportExportService;
    }

    @GetMapping("/experiment-forms/{id}/export")
    public ResponseEntity<byte[]> exportExperimentForm(@PathVariable String id, HttpServletRequest request) {
        return file(reportExportService.exportExperimentForm(id,principal(request)));
    }

    @GetMapping("/test-records/{id}/export")
    public ResponseEntity<byte[]> exportTestRecord(@PathVariable String id, HttpServletRequest request) {
        return file(reportExportService.exportTestRecord(id,principal(request)));
    }

    @GetMapping("/pricing-files/{id}/export")
    public ResponseEntity<byte[]> exportPricingFile(@PathVariable String id, HttpServletRequest request) {
        var principal = (SessionPrincipal) request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
        return file(reportExportService.exportPricingFile(
                id,
                principal == null ? null : principal.role(),
                principal == null ? null : principal.name()
        ));
    }

    @GetMapping("/rnd-tasks/export")
    public ResponseEntity<byte[]> exportRndTasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, HttpServletRequest request
    ) {
        return file(reportExportService.exportRndTasks(keyword, status, startDate, endDate,principal(request)));
    }

    @GetMapping("/shipments/export")
    public ResponseEntity<byte[]> exportShipments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, HttpServletRequest request
    ) {
        return file(reportExportService.exportShipments(keyword, status, startDate, endDate,principal(request)));
    }

    private ResponseEntity<byte[]> file(ReportExportFile file) {
        var encodedFileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(file.content());
    }
    private SessionPrincipal principal(HttpServletRequest request) {
        return (SessionPrincipal)request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
    }
}
