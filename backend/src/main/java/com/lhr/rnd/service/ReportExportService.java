package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.persistence.entity.ArchiveFileEntity;
import com.lhr.rnd.persistence.entity.ExperimentFormEntity;
import com.lhr.rnd.persistence.entity.RndTaskEntity;
import com.lhr.rnd.persistence.repository.ArchiveFileRepository;
import com.lhr.rnd.persistence.repository.ExperimentFormRepository;
import com.lhr.rnd.persistence.repository.ExperimentMaterialRepository;
import com.lhr.rnd.persistence.repository.ExperimentProcessRepository;
import com.lhr.rnd.persistence.repository.RndTaskRepository;
import com.lhr.rnd.persistence.repository.ShipmentRecordRepository;
import com.lhr.rnd.persistence.repository.TestRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ReportExportService {
    public static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Clock clock;
    private final ExperimentFormRepository experimentFormRepository;
    private final ExperimentMaterialRepository experimentMaterialRepository;
    private final ExperimentProcessRepository experimentProcessRepository;
    private final RndTaskRepository rndTaskRepository;
    private final TestRecordRepository testRecordRepository;
    private final ShipmentRecordRepository shipmentRecordRepository;
    private final ArchiveFileRepository archiveFileRepository;
    private final LocalArchiveStorageService archiveStorageService;
    private final SampleWorkflowService workflowService;

    @Autowired
    public ReportExportService(
            ExperimentFormRepository experimentFormRepository,
            ExperimentMaterialRepository experimentMaterialRepository,
            ExperimentProcessRepository experimentProcessRepository,
            RndTaskRepository rndTaskRepository,
            TestRecordRepository testRecordRepository,
            ShipmentRecordRepository shipmentRecordRepository,
            ArchiveFileRepository archiveFileRepository,
            LocalArchiveStorageService archiveStorageService,
            SampleWorkflowService workflowService
    ) {
        this(
                Clock.systemDefaultZone(),
                experimentFormRepository,
                experimentMaterialRepository,
                experimentProcessRepository,
                rndTaskRepository,
                testRecordRepository,
                shipmentRecordRepository,
                archiveFileRepository,
                archiveStorageService,
                workflowService
        );
    }

    ReportExportService(
            Clock clock,
            ExperimentFormRepository experimentFormRepository,
            ExperimentMaterialRepository experimentMaterialRepository,
            ExperimentProcessRepository experimentProcessRepository,
            RndTaskRepository rndTaskRepository,
            TestRecordRepository testRecordRepository,
            ShipmentRecordRepository shipmentRecordRepository,
            ArchiveFileRepository archiveFileRepository,
            LocalArchiveStorageService archiveStorageService,
            SampleWorkflowService workflowService
    ) {
        this.clock = clock;
        this.experimentFormRepository = experimentFormRepository;
        this.experimentMaterialRepository = experimentMaterialRepository;
        this.experimentProcessRepository = experimentProcessRepository;
        this.rndTaskRepository = rndTaskRepository;
        this.testRecordRepository = testRecordRepository;
        this.shipmentRecordRepository = shipmentRecordRepository;
        this.archiveFileRepository = archiveFileRepository;
        this.archiveStorageService = archiveStorageService;
        this.workflowService = workflowService;
    }

    @Transactional
    public ReportExportFile exportExperimentForm(String experimentFormId) {
        var form = experimentFormRepository.findById(experimentFormId)
                .orElseThrow(() -> new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在"));
        var materials = experimentMaterialRepository.findByExperimentFormIdOrderBySequenceAsc(experimentFormId);
        var processSteps = experimentProcessRepository.findByExperimentFormIdOrderBySequenceAsc(experimentFormId);

        var content = workbook(output -> {
            var sheet = output.createSheet("实验单");
            put(sheet.createRow(0), "打样实验单");
            put(sheet.createRow(2), "产品名称", form.getProductName(), "样品编号", form.getSampleNo());
            put(sheet.createRow(3), "版本", form.getVersionCode(), "状态", form.getStatus());
            put(sheet.createRow(4), "研发人员", form.getOperatorName(), "保存时间", text(form.getSavedAt()));
            put(sheet.createRow(5), "实验总结", form.getSummary());
            put(sheet.createRow(6), "成品实际产出kg", form.getFinishedOutputWeightKg(), "成品得率", formatRate(form.getFinishedYieldRatio()));
            put(sheet.createRow(8), "类别", "主原料", "序号", "物料编码", "物料名称", "配方比例", "重量kg", "单位", "利用率", "备注");
            var rowIndex = 9;
            for (var material : materials) {
                put(sheet.createRow(rowIndex++),
                        materialCategoryText(material.getMaterialCategory()),
                        Boolean.TRUE.equals(material.getPrimaryMaterial()) ? "是" : "",
                        material.getSequence(),
                        material.getMaterialCode(),
                        material.getMaterialName(),
                        formatRate(material.getFormulaRatio()),
                        material.getWeightKg(),
                        text(material.getInputUnit()).isBlank() ? "kg" : material.getInputUnit(),
                        formatRate(material.getUtilizationRate()),
                        material.getRemark());
            }
            rowIndex = Math.max(rowIndex + 1, 14);
            put(sheet.createRow(rowIndex++), "序号", "工序", "投入重量kg", "下一步出成kg", "余料重量kg", "余料去向", "损耗重量kg", "损耗率", "备注");
            for (var step : processSteps) {
                put(sheet.createRow(rowIndex++),
                        step.getSequence(),
                        step.getProcessName(),
                        step.getBeforeWeightKg(),
                        step.getAfterWeightKg(),
                        step.getRemainingWeightKg(),
                        remainingDispositionText(step.getRemainingDisposition()),
                        step.getLossWeightKg(),
                        formatRate(step.getLossRate()),
                        step.getRemark());
            }
        });
        var fileName = form.getProductName() + "-" + form.getVersionCode() + "-打样实验单-" + today() + ".xlsx";
        archive("REPORT_EXPERIMENT_FORM", experimentFormId, form.getVersionId(), form.getSampleNo(), form.getVersionCode(), fileName, content);
        return new ReportExportFile(fileName, EXCEL_CONTENT_TYPE, content);
    }

    @Transactional
    public ReportExportFile exportRndTasks(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        var normalizedKeyword = keyword == null ? "" : keyword.trim();
        var normalizedStatus = status == null ? "" : status.trim();
        var content = workbook(output -> {
            var sheet = output.createSheet("打样任务列表");
            put(sheet.createRow(0), "打样任务列表");
            put(sheet.createRow(1), "任务编号", "样品编号", "产品名称", "版本", "状态", "负责人", "截止日期", "创建时间");
            var rowIndex = 2;
            for (var task : rndTaskRepository.findAll()) {
                if (!matchesTask(task, normalizedKeyword, normalizedStatus, startDate, endDate)) {
                    continue;
                }
                put(sheet.createRow(rowIndex++),
                        task.getId(),
                        task.getSampleNo(),
                        task.getProductName(),
                        task.getVersionCode(),
                        task.getStatus(),
                        task.getAssigneeName(),
                        text(task.getDueDate()),
                        text(task.getCreatedAt()));
            }
        });
        var fileName = "打样任务列表-" + today() + ".xlsx";
        archive("REPORT_RND_TASK_LIST", "RND_TASK_LIST", null, "reports", "list", fileName, content);
        return new ReportExportFile(fileName, EXCEL_CONTENT_TYPE, content);
    }

    @Transactional
    public ReportExportFile exportTestRecord(String testRecordId) {
        var record = testRecordRepository.findById(testRecordId)
                .orElseThrow(() -> new BusinessException("TEST_RECORD_NOT_FOUND", "测试记录不存在"));
        var content = workbook(output -> {
            var sheet = output.createSheet("测试单");
            put(sheet.createRow(0), "内部测试单");
            put(sheet.createRow(2), "测试人", field(record, "testerName"), "测试结果", field(record, "result"));
            put(sheet.createRow(3), "测试时间", field(record, "testedAt"));
            put(sheet.createRow(5), "测试意见", field(record, "comment"));
        });
        var fileName = "内部测试单-" + testRecordId + "-" + today() + ".xlsx";
        archive("REPORT_TEST_RECORD", testRecordId, null, "reports", "test", fileName, content);
        return new ReportExportFile(fileName, EXCEL_CONTENT_TYPE, content);
    }

    public ReportExportFile exportPricingFile(String pricingFileId) {
        var file = workflowService.downloadPricingFile(pricingFileId);
        return new ReportExportFile(file.fileName(), EXCEL_CONTENT_TYPE, file.content());
    }

    @Transactional
    public ReportExportFile exportShipments(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        var normalizedKeyword = keyword == null ? "" : keyword.trim();
        var normalizedStatus = status == null ? "" : status.trim();
        var content = workbook(output -> {
            var sheet = output.createSheet("寄样反馈列表");
            put(sheet.createRow(0), "寄样反馈列表");
            put(sheet.createRow(1), "寄样编号", "样品编号", "产品名称", "版本", "状态", "数量", "收件人", "快递单号", "寄样时间");
            var rowIndex = 2;
            for (var shipment : shipmentRecordRepository.findAll()) {
                var text = field(shipment, "sampleNo") + field(shipment, "productName") + field(shipment, "trackingNo");
                if (!normalizedKeyword.isBlank() && !text.contains(normalizedKeyword)) {
                    continue;
                }
                if (!normalizedStatus.isBlank() && !normalizedStatus.equals(field(shipment, "status"))) {
                    continue;
                }
                put(sheet.createRow(rowIndex++),
                        field(shipment, "id"),
                        field(shipment, "sampleNo"),
                        field(shipment, "productName"),
                        field(shipment, "versionCode"),
                        field(shipment, "status"),
                        field(shipment, "quantity"),
                        field(shipment, "receiverName"),
                        field(shipment, "trackingNo"),
                        field(shipment, "shippedAt"));
            }
        });
        var fileName = "寄样反馈列表-" + today() + ".xlsx";
        archive("REPORT_SHIPMENT_LIST", "SHIPMENT_LIST", null, "reports", "shipment", fileName, content);
        return new ReportExportFile(fileName, EXCEL_CONTENT_TYPE, content);
    }

    private boolean matchesTask(RndTaskEntity task, String keyword, String status, LocalDate startDate, LocalDate endDate) {
        var haystack = task.getSampleNo() + task.getProductName() + text(task.getAssigneeName());
        if (!keyword.isBlank() && !haystack.contains(keyword)) {
            return false;
        }
        if (!status.isBlank() && !status.equals(task.getStatus())) {
            return false;
        }
        var createdDate = task.getCreatedAt() == null ? null : task.getCreatedAt().toLocalDate();
        if (startDate != null && (createdDate == null || createdDate.isBefore(startDate))) {
            return false;
        }
        return endDate == null || (createdDate != null && !createdDate.isAfter(endDate));
    }

    private void archive(
            String businessType,
            String businessId,
            String versionId,
            String sampleNo,
            String versionCode,
            String fileName,
            byte[] content
    ) {
        var archiveId = nextId("ARF");
        var path = sampleNo + "/" + versionCode + "/报表/" + archiveId + "/" + fileName;
        archiveStorageService.store(path, content);
        archiveFileRepository.save(new ArchiveFileEntity(
                archiveId,
                businessType,
                businessId,
                versionId,
                fileName,
                path,
                null,
                "REPORT",
                "系统导出",
                "报表导出",
                EXCEL_CONTENT_TYPE,
                (long) content.length,
                "ARCHIVED",
                LocalDateTime.now(clock)
        ));
    }

    private byte[] workbook(WorkbookWriter writer) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            writer.write(workbook);
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                for (int column = 0; column < 12; column++) {
                    sheet.autoSizeColumn(column);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("REPORT_EXPORT_FAILED", "报表导出失败");
        }
    }

    private void put(Row row, Object... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(text(values[index]));
        }
    }

    private String field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return text(field.get(target));
        } catch (Exception exception) {
            return "";
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String formatRate(java.math.BigDecimal rate) {
        if (rate == null) {
            return "";
        }
        return rate.multiply(new java.math.BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private String materialCategoryText(String category) {
        if ("AUXILIARY".equals(category)) {
            return "辅料";
        }
        if ("PACKAGING".equals(category)) {
            return "包材";
        }
        return "原料";
    }

    private String remainingDispositionText(String disposition) {
        if ("RETURN".equals(disposition) || "RETURNED".equals(disposition)) {
            return "退回";
        }
        if ("DISCARD".equals(disposition) || "DISCARDED".equals(disposition)) {
            return "废弃";
        }
        if ("REUSE".equals(disposition) || "REUSED".equals(disposition)) {
            return "回用";
        }
        return text(disposition);
    }

    private String today() {
        return LocalDate.now(clock).format(DATE);
    }

    private String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
    }

    private interface WorkbookWriter {
        void write(XSSFWorkbook workbook);
    }
}
