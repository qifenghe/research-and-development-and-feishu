package com.lhr.rnd.service;

import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.ExperimentProcessStep;
import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.ShipmentRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FeedbackDemoSeedService {
    private static final String ASSISTANT = "赵内勤";
    private static final String DIRECTOR = "赵总监";
    private static final String ENGINEER = "张研发";
    private static final String TESTER = "李测试";

    private final SampleWorkflowService workflowService;
    private final WorkflowSettingsService workflowSettingsService;
    private final RolePermissionService rolePermissionService;

    public FeedbackDemoSeedService(
            SampleWorkflowService workflowService,
            WorkflowSettingsService workflowSettingsService,
            RolePermissionService rolePermissionService
    ) {
        this.workflowService = workflowService;
        this.workflowSettingsService = workflowSettingsService;
        this.rolePermissionService = rolePermissionService;
    }

    public boolean alreadySeeded() {
        return workflowService.hasWorkflowData();
    }

    @Transactional
    public FeedbackDemoSeedResult seedIfEmpty() {
        workflowSettingsService.initializeDefaultWorkflows();
        rolePermissionService.initializeDefaultPermissions();
        if (alreadySeeded()) {
            return snapshot("演示数据已存在，无需重复初始化");
        }

        var pendingShipmentTask = seedCompletedTask("红烧牛腩粒", "500g/袋", "速冻调理");
        seedCompletedTaskWithShipment("500g香卤大肠头", "500g/袋", "冷冻即热菜");

        var shipped = workflowService.shipments("SHIPPED", null).size();
        var pendingShipment = workflowService.tasks("COMPLETED", null).stream()
                .filter(task -> workflowService.shipments(null, task.sampleNo()).stream()
                        .noneMatch(item -> item.versionId().equals(task.versionId())))
                .count();

        return new FeedbackDemoSeedResult(
                (int) pendingShipment,
                shipped,
                pendingShipmentTask.id(),
                "已生成演示样品：1 条待登记寄样、1 条待录入反馈"
        );
    }

    private RndTask seedCompletedTask(String productName, String specification, String productType) {
        var task = seedToCompleted(productName, specification, productType);
        return task;
    }

    private ShipmentRecord seedCompletedTaskWithShipment(String productName, String specification, String productType) {
        var task = seedToCompleted(productName, specification, productType);
        return workflowService.createShipment(new SampleWorkflowService.CreateShipmentCommand(
                task.versionId(),
                6,
                "销售内勤",
                "SF" + System.currentTimeMillis() % 1_000_000_000L,
                "寄客户试吃确认"
        ));
    }

    private RndTask seedToCompleted(String productName, String specification, String productType) {
        var request = workflowService.createRequest(new SampleWorkflowService.CreateSampleRequestCommand(
                productName,
                productType,
                "LHYC",
                specification,
                "餐饮渠道试吃",
                "香卤风味",
                ASSISTANT
        ));
        var approved = workflowService.approveRequest(request.id(), DIRECTOR);
        var taskId = approved.task().id();
        workflowService.assignTask(taskId, ENGINEER, LocalDate.now().plusDays(3));
        workflowService.acceptTask(taskId, ENGINEER);

        var form = workflowService.saveExperimentDraft(new SampleWorkflowService.SaveExperimentDraftCommand(
                taskId,
                ENGINEER,
                productName + " 打样实验记录",
                defaultMaterials(),
                defaultProcessSteps(),
                new BigDecimal("98"),
                196,
                "袋",
                null
        ));
        var submitted = workflowService.submitExperimentForTest(form.id(), TESTER);
        var passed = workflowService.passInternalTest(
                submitted.testAssignment().id(),
                TESTER,
                "口味、口感、复热状态通过，可寄样"
        );
        return passed.task();
    }

    private List<ExperimentMaterial> defaultMaterials() {
        return List.of(
                new ExperimentMaterial(
                        "原料",
                        1,
                        "YL-001",
                        "主原料",
                        new BigDecimal("100"),
                        new BigDecimal("0.82"),
                        "按实际投入量记录",
                        "RAW",
                        true,
                        null,
                        "kg"
                )
        );
    }

    private List<ExperimentProcessStep> defaultProcessSteps() {
        return List.of(new ExperimentProcessStep(
                1,
                "蒸煮",
                new BigDecimal("100"),
                new BigDecimal("98"),
                new BigDecimal("1"),
                "REUSE",
                null,
                null,
                "演示打样关键工序"
        ));
    }

    private FeedbackDemoSeedResult snapshot(String message) {
        var shipped = workflowService.shipments("SHIPPED", null);
        var completed = workflowService.tasks("COMPLETED", null);
        var allShipments = workflowService.shipments(null, null);
        var pendingShipment = completed.stream()
                .filter(task -> allShipments.stream().noneMatch(item -> item.versionId().equals(task.versionId())))
                .count();
        return new FeedbackDemoSeedResult(
                (int) pendingShipment,
                shipped.size(),
                completed.isEmpty() ? null : completed.get(0).id(),
                message
        );
    }

    public record FeedbackDemoSeedResult(
            int pendingShipmentCount,
            int pendingFeedbackCount,
            String sampleTaskId,
            String message
    ) {
    }
}
