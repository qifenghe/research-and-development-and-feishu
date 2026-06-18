package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.SampleStatus;
import com.lhr.rnd.domain.SampleVersionCode;
import com.lhr.rnd.model.CustomerFeedback;
import com.lhr.rnd.model.CustomerFeedbackResult;
import com.lhr.rnd.model.FinanceNotification;
import com.lhr.rnd.model.FinanceNotificationStatus;
import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.RndTaskStatus;
import com.lhr.rnd.model.ExperimentForm;
import com.lhr.rnd.model.ExperimentFormStatus;
import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.PricingFileRecord;
import com.lhr.rnd.model.PricingFileStatus;
import com.lhr.rnd.model.SampleProject;
import com.lhr.rnd.model.SampleRequest;
import com.lhr.rnd.model.SampleVersion;
import com.lhr.rnd.model.ShipmentRecord;
import com.lhr.rnd.model.ShipmentStatus;
import com.lhr.rnd.model.TestAssignment;
import com.lhr.rnd.model.TestAssignmentStatus;
import com.lhr.rnd.model.TestRecord;
import com.lhr.rnd.persistence.entity.ExperimentFormEntity;
import com.lhr.rnd.persistence.entity.ExperimentMaterialEntity;
import com.lhr.rnd.persistence.entity.RndTaskEntity;
import com.lhr.rnd.persistence.entity.SampleProjectEntity;
import com.lhr.rnd.persistence.entity.SampleRequestEntity;
import com.lhr.rnd.persistence.entity.SampleVersionEntity;
import com.lhr.rnd.persistence.repository.ExperimentFormRepository;
import com.lhr.rnd.persistence.repository.ExperimentMaterialRepository;
import com.lhr.rnd.persistence.repository.RndTaskRepository;
import com.lhr.rnd.persistence.repository.SampleProjectRepository;
import com.lhr.rnd.persistence.repository.SampleRequestRepository;
import com.lhr.rnd.persistence.repository.SampleVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SampleWorkflowService {
    private final Clock clock;
    private final PricingFileService pricingFileService = new PricingFileService();
    private final SampleRequestRepository sampleRequestRepository;
    private final SampleProjectRepository sampleProjectRepository;
    private final SampleVersionRepository sampleVersionRepository;
    private final RndTaskRepository rndTaskRepository;
    private final ExperimentFormRepository experimentFormRepository;
    private final ExperimentMaterialRepository experimentMaterialRepository;
    private final Map<String, SampleRequest> requests = new LinkedHashMap<>();
    private final Map<String, SampleProject> projects = new LinkedHashMap<>();
    private final Map<String, SampleVersion> versions = new LinkedHashMap<>();
    private final Map<String, RndTask> tasks = new LinkedHashMap<>();
    private final Map<String, ExperimentForm> experimentForms = new LinkedHashMap<>();
    private final Map<String, TestAssignment> testAssignments = new LinkedHashMap<>();
    private final Map<String, TestRecord> testRecords = new LinkedHashMap<>();
    private final Map<String, ShipmentRecord> shipments = new LinkedHashMap<>();
    private final Map<String, CustomerFeedback> customerFeedbacks = new LinkedHashMap<>();
    private final Map<String, PricingFileRecord> pricingFiles = new LinkedHashMap<>();
    private final Map<String, FinanceNotification> financeNotifications = new LinkedHashMap<>();

    private int requestSequence = 1;
    private int taskSequence = 1;
    private int experimentSequence = 1;
    private int testAssignmentSequence = 1;
    private int testRecordSequence = 1;
    private int shipmentSequence = 1;
    private int customerFeedbackSequence = 1;
    private int pricingFileSequence = 1;
    private int financeNotificationSequence = 1;

    public SampleWorkflowService() {
        this(Clock.systemDefaultZone(), null, null, null, null, null, null);
    }

    SampleWorkflowService(Clock clock) {
        this(clock, null, null, null, null, null, null);
    }

    @Autowired
    public SampleWorkflowService(
            SampleRequestRepository sampleRequestRepository,
            SampleProjectRepository sampleProjectRepository,
            SampleVersionRepository sampleVersionRepository,
            RndTaskRepository rndTaskRepository,
            ExperimentFormRepository experimentFormRepository,
            ExperimentMaterialRepository experimentMaterialRepository
    ) {
        this(
                Clock.systemDefaultZone(),
                sampleRequestRepository,
                sampleProjectRepository,
                sampleVersionRepository,
                rndTaskRepository,
                experimentFormRepository,
                experimentMaterialRepository
        );
    }

    private SampleWorkflowService(
            Clock clock,
            SampleRequestRepository sampleRequestRepository,
            SampleProjectRepository sampleProjectRepository,
            SampleVersionRepository sampleVersionRepository,
            RndTaskRepository rndTaskRepository,
            ExperimentFormRepository experimentFormRepository,
            ExperimentMaterialRepository experimentMaterialRepository
    ) {
        this.clock = clock;
        this.sampleRequestRepository = sampleRequestRepository;
        this.sampleProjectRepository = sampleProjectRepository;
        this.sampleVersionRepository = sampleVersionRepository;
        this.rndTaskRepository = rndTaskRepository;
        this.experimentFormRepository = experimentFormRepository;
        this.experimentMaterialRepository = experimentMaterialRepository;
    }

    @Transactional
    public synchronized SampleRequest createRequest(CreateSampleRequestCommand command) {
        var sampleNo = "YP20260618" + "%04d".formatted(requestSequence);
        var request = new SampleRequest(
                "REQ-%04d".formatted(requestSequence),
                sampleNo,
                command.productName(),
                command.productType(),
                command.customerName(),
                command.specification(),
                command.creatorName(),
                SampleStatus.PENDING_REVIEW,
                now()
        );
        requestSequence++;
        requests.put(request.id(), request);
        persistRequest(request);
        return request;
    }

    @Transactional
    public synchronized ApproveSampleRequestResult approveRequest(String requestId, String reviewerName) {
        var request = requests.get(requestId);
        if (request == null) {
            throw new BusinessException("SAMPLE_REQUEST_NOT_FOUND", "样品需求不存在");
        }
        if (request.status() != SampleStatus.PENDING_REVIEW) {
            throw new BusinessException("SAMPLE_REQUEST_STATUS_ILLEGAL", "当前需求状态不可审核");
        }

        var project = new SampleProject(
                "PROJ-%04d".formatted(projects.size() + 1),
                request.sampleNo(),
                request.productName(),
                request.productType(),
                request.customerName(),
                request.specification(),
                SampleStatus.PENDING_ASSIGNMENT,
                now()
        );
        var versionCode = SampleVersionCode.fromNumber(0).code();
        var version = SampleVersion.builder()
                .id("VER-%04d".formatted(versions.size() + 1))
                .projectId(project.id())
                .sampleNo(request.sampleNo())
                .productName(request.productName())
                .productType(request.productType())
                .specification(request.specification())
                .versionNo(versionCode)
                .versionNumber(0)
                .versionCode(versionCode)
                .ownerName(reviewerName)
                .authorName(request.creatorName())
                .createdAt(now())
                .build();
        var task = new RndTask(
                "TASK-%04d".formatted(taskSequence),
                project.id(),
                version.id(),
                request.sampleNo(),
                request.productName(),
                version.versionCode(),
                RndTaskStatus.PENDING_ASSIGNMENT,
                null,
                null,
                now(),
                null
        );
        taskSequence++;

        projects.put(project.id(), project);
        versions.put(version.id(), version);
        tasks.put(task.id(), task);
        persistApprovedWorkflow(request, project, version, task);
        return new ApproveSampleRequestResult(project, version, task);
    }

    public synchronized List<RndTask> taskPool() {
        return tasks.values().stream()
                .filter(task -> task.status() == RndTaskStatus.PENDING_ASSIGNMENT)
                .toList();
    }

    @Transactional
    public synchronized RndTask assignTask(String taskId, String assigneeName, LocalDate dueDate) {
        var task = tasks.get(taskId);
        if (task == null) {
            throw new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在");
        }
        if (task.status() != RndTaskStatus.PENDING_ASSIGNMENT) {
            throw new BusinessException("RND_TASK_STATUS_ILLEGAL", "当前任务状态不可分发");
        }
        var assigned = task.assign(assigneeName, dueDate, now());
        tasks.put(taskId, assigned);
        persistAssignedTask(assigned);
        return assigned;
    }

    @Transactional
    public synchronized RndTask acceptTask(String taskId, String acceptedBy) {
        var task = tasks.get(taskId);
        if (task == null) {
            throw new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在");
        }
        if (task.status() != RndTaskStatus.PENDING_ACCEPTANCE) {
            throw new BusinessException("RND_TASK_STATUS_ILLEGAL", "当前任务状态不可接受");
        }
        if (!acceptedBy.equals(task.assigneeName())) {
            throw new BusinessException("RND_TASK_ASSIGNEE_MISMATCH", "只能由被分发的研发人员接受任务");
        }
        var acceptedAt = now();
        var accepted = task.accept(acceptedAt);
        tasks.put(taskId, accepted);
        persistAcceptedTask(accepted, acceptedAt);
        return accepted;
    }

    @Transactional
    public synchronized ExperimentForm saveExperimentDraft(SaveExperimentDraftCommand command) {
        var task = tasks.get(command.taskId());
        if (task == null) {
            throw new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在");
        }
        if (task.status() != RndTaskStatus.SAMPLING) {
            throw new BusinessException("RND_TASK_STATUS_ILLEGAL", "任务接受后才能填写实验单");
        }

        var existing = experimentForms.values().stream()
                .filter(form -> form.taskId().equals(command.taskId()))
                .findFirst();
        var id = existing.map(ExperimentForm::id).orElse("EXP-%04d".formatted(experimentSequence++));
        var draft = new ExperimentForm(
                id,
                task.id(),
                task.projectId(),
                task.versionId(),
                task.sampleNo(),
                task.productName(),
                task.versionCode(),
                ExperimentFormStatus.DRAFT,
                command.operatorName(),
                command.summary(),
                command.materials() == null ? List.of() : List.copyOf(command.materials()),
                now(),
                null
        );
        experimentForms.put(draft.id(), draft);
        persistExperimentDraft(draft);
        return draft;
    }

    public synchronized SubmitExperimentForTestResult submitExperimentForTest(String experimentFormId, String testerName) {
        var form = experimentForms.get(experimentFormId);
        if (form == null) {
            throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
        }
        if (form.status() != ExperimentFormStatus.DRAFT) {
            throw new BusinessException("EXPERIMENT_FORM_STATUS_ILLEGAL", "只有草稿实验单可以提交测试");
        }
        var submitted = form.submit(now());
        experimentForms.put(submitted.id(), submitted);

        var task = tasks.get(submitted.taskId());
        if (task != null) {
            tasks.put(task.id(), new RndTask(
                    task.id(),
                    task.projectId(),
                    task.versionId(),
                    task.sampleNo(),
                    task.productName(),
                    task.versionCode(),
                    RndTaskStatus.PENDING_TEST,
                    task.assigneeName(),
                    task.dueDate(),
                    task.createdAt(),
                    task.assignedAt()
            ));
        }

        var assignment = new TestAssignment(
                "TEST-%04d".formatted(testAssignmentSequence++),
                submitted.id(),
                submitted.taskId(),
                submitted.versionId(),
                testerName,
                TestAssignmentStatus.PENDING_TEST,
                now()
        );
        testAssignments.put(assignment.id(), assignment);
        return new SubmitExperimentForTestResult(submitted, assignment);
    }

    public synchronized PassInternalTestResult passInternalTest(String testAssignmentId, String testerName, String comment) {
        var assignment = pendingTestAssignment(testAssignmentId, testerName);
        var form = experimentForms.get(assignment.experimentFormId());
        if (form == null) {
            throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
        }
        var locked = form.lock();
        experimentForms.put(locked.id(), locked);

        var passed = assignment.withStatus(TestAssignmentStatus.PASSED);
        testAssignments.put(passed.id(), passed);
        var record = testRecord(passed, testerName, TestAssignmentStatus.PASSED, comment);

        var task = tasks.get(assignment.taskId());
        var completedTask = task == null ? null : task.withStatus(RndTaskStatus.COMPLETED);
        if (completedTask != null) {
            tasks.put(completedTask.id(), completedTask);
        }
        return new PassInternalTestResult(locked, passed, record, completedTask);
    }

    public synchronized FailInternalTestResult failInternalTestForResample(String testAssignmentId, String testerName, String comment) {
        var assignment = pendingTestAssignment(testAssignmentId, testerName);
        var failed = assignment.withStatus(TestAssignmentStatus.FAILED_RESAMPLE);
        testAssignments.put(failed.id(), failed);
        var record = testRecord(failed, testerName, TestAssignmentStatus.FAILED_RESAMPLE, comment);

        var currentVersion = versions.get(assignment.versionId());
        if (currentVersion == null) {
            throw new BusinessException("SAMPLE_VERSION_NOT_FOUND", "样品版本不存在");
        }
        var nextNumber = currentVersion.versionNumber() == null ? 1 : currentVersion.versionNumber() + 1;
        var nextCode = SampleVersionCode.fromNumber(nextNumber).code();
        var nextVersion = SampleVersion.builder()
                .id("VER-%04d".formatted(versions.size() + 1))
                .projectId(currentVersion.projectId())
                .sampleNo(currentVersion.sampleNo())
                .productName(currentVersion.productName())
                .productType(currentVersion.productType())
                .specification(currentVersion.specification())
                .versionNo(nextCode)
                .versionNumber(nextNumber)
                .versionCode(nextCode)
                .ownerName(currentVersion.ownerName())
                .authorName(currentVersion.authorName())
                .createdAt(now())
                .build();
        versions.put(nextVersion.id(), nextVersion);

        var previousTask = tasks.get(assignment.taskId());
        if (previousTask != null) {
            tasks.put(previousTask.id(), previousTask.withStatus(RndTaskStatus.COMPLETED));
        }
        var nextTask = new RndTask(
                "TASK-%04d".formatted(taskSequence++),
                nextVersion.projectId(),
                nextVersion.id(),
                nextVersion.sampleNo(),
                nextVersion.productName(),
                nextVersion.versionCode(),
                RndTaskStatus.PENDING_ACCEPTANCE,
                previousTask == null ? null : previousTask.assigneeName(),
                previousTask == null ? null : previousTask.dueDate(),
                now(),
                null
        );
        tasks.put(nextTask.id(), nextTask);
        return new FailInternalTestResult(failed, record, nextVersion, nextTask);
    }

    public synchronized List<SampleRequest> requests() {
        return new ArrayList<>(requests.values());
    }

    public synchronized ShipmentRecord createShipment(CreateShipmentCommand command) {
        var version = requiredVersion(command.versionId());
        ensureReadyForShipment(command.versionId());
        var shipment = new ShipmentRecord(
                "SHIP-%04d".formatted(shipmentSequence++),
                version.id(),
                version.sampleNo(),
                version.productName(),
                version.versionCode(),
                command.quantity(),
                command.receiverName(),
                command.trackingNo(),
                command.remark(),
                ShipmentStatus.SHIPPED,
                now()
        );
        shipments.put(shipment.id(), shipment);
        return shipment;
    }

    public synchronized ShipmentFeedbackResult submitCustomerFeedback(SubmitCustomerFeedbackCommand command) {
        var shipment = shipments.get(command.shipmentId());
        if (shipment == null) {
            throw new BusinessException("SHIPMENT_NOT_FOUND", "寄样记录不存在");
        }
        if (shipment.status() != ShipmentStatus.SHIPPED) {
            throw new BusinessException("SHIPMENT_STATUS_ILLEGAL", "当前寄样状态不可反馈");
        }

        var nextStatus = switch (command.result()) {
            case PASSED -> ShipmentStatus.FEEDBACK_PASSED;
            case FAILED_RESAMPLE -> ShipmentStatus.FEEDBACK_FAILED_RESAMPLE;
            case STOPPED -> ShipmentStatus.STOPPED;
        };
        var updatedShipment = shipment.withStatus(nextStatus);
        shipments.put(updatedShipment.id(), updatedShipment);

        var feedback = new CustomerFeedback(
                "CFB-%04d".formatted(customerFeedbackSequence++),
                shipment.id(),
                command.feedbackBy(),
                command.result(),
                command.comment(),
                now()
        );
        customerFeedbacks.put(feedback.id(), feedback);
        return new ShipmentFeedbackResult(updatedShipment, feedback);
    }

    public synchronized PricingFileRecord generatePricingFile(String versionId) {
        var version = requiredVersion(versionId);
        var lockedForm = lockedExperimentForm(versionId);
        var pricingVersionNo = "V" + nextPricingVersionNumber(versionId);
        var versionWithMaterials = SampleVersion.builder()
                .id(version.id())
                .projectId(version.projectId())
                .sampleNo(version.sampleNo())
                .productName(version.productName())
                .productType(version.productType())
                .specification(version.specification())
                .versionNo(version.versionNo())
                .versionNumber(version.versionNumber())
                .versionCode(version.versionCode())
                .ownerName(version.ownerName())
                .authorName(lockedForm.operatorName())
                .effectiveDate(version.effectiveDate())
                .referenceOutputKg(version.referenceOutputKg())
                .unitWeightKg(version.unitWeightKg())
                .materials(lockedForm.materials())
                .createdAt(version.createdAt())
                .build();
        var generated = pricingFileService.generate(versionWithMaterials, pricingVersionNo);
        var record = new PricingFileRecord(
                "PRICE-%04d".formatted(pricingFileSequence++),
                version.id(),
                version.sampleNo(),
                version.productName(),
                version.versionCode(),
                generated.pricingVersion(),
                generated.fileName(),
                PricingFileStatus.GENERATED,
                generated.content().length,
                now()
        );
        pricingFiles.put(record.id(), record);
        return record;
    }

    public synchronized NotifyFinanceResult notifyFinance(String pricingFileId, String recipientName, String remark) {
        var pricingFile = pricingFiles.get(pricingFileId);
        if (pricingFile == null) {
            throw new BusinessException("PRICING_FILE_NOT_FOUND", "核价文件不存在");
        }
        var notifiedPricingFile = pricingFile.withStatus(PricingFileStatus.FINANCE_NOTIFIED);
        pricingFiles.put(notifiedPricingFile.id(), notifiedPricingFile);
        var notification = new FinanceNotification(
                "FIN-%04d".formatted(financeNotificationSequence++),
                pricingFile.id(),
                recipientName,
                remark,
                FinanceNotificationStatus.SENT,
                now()
        );
        financeNotifications.put(notification.id(), notification);
        return new NotifyFinanceResult(notifiedPricingFile, notification);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void persistRequest(SampleRequest request) {
        if (sampleRequestRepository == null) {
            return;
        }
        sampleRequestRepository.save(new SampleRequestEntity(
                request.id(),
                request.sampleNo(),
                request.productName(),
                request.productType(),
                request.customerName(),
                request.specification(),
                request.creatorName(),
                request.status().name(),
                request.createdAt()
        ));
    }

    private void persistApprovedWorkflow(
            SampleRequest request,
            SampleProject project,
            SampleVersion version,
            RndTask task
    ) {
        if (sampleProjectRepository == null || sampleVersionRepository == null || rndTaskRepository == null) {
            return;
        }
        sampleProjectRepository.save(new SampleProjectEntity(
                project.id(),
                request.id(),
                project.sampleNo(),
                project.productName(),
                project.productType(),
                project.customerName(),
                project.specification(),
                project.status().name(),
                project.createdAt()
        ));
        sampleVersionRepository.save(new SampleVersionEntity(
                version.id(),
                version.projectId(),
                version.sampleNo(),
                version.productName(),
                version.productType(),
                version.specification(),
                version.versionNo(),
                version.versionNumber(),
                version.versionCode(),
                version.ownerName(),
                version.authorName(),
                version.effectiveDate(),
                version.referenceOutputKg(),
                version.unitWeightKg(),
                version.createdAt()
        ));
        rndTaskRepository.save(new RndTaskEntity(
                task.id(),
                task.projectId(),
                task.versionId(),
                task.sampleNo(),
                task.productName(),
                task.versionCode(),
                task.status().name(),
                task.assigneeName(),
                task.dueDate(),
                task.createdAt(),
                task.assignedAt(),
                null
        ));
    }

    private void persistAssignedTask(RndTask task) {
        if (rndTaskRepository == null) {
            return;
        }
        var taskEntity = rndTaskRepository.findById(task.id())
                .orElseThrow(() -> new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在"));
        taskEntity.assign(task.assigneeName(), task.dueDate(), task.assignedAt());
        rndTaskRepository.save(taskEntity);
    }

    private void persistAcceptedTask(RndTask task, LocalDateTime acceptedAt) {
        if (rndTaskRepository == null) {
            return;
        }
        var taskEntity = rndTaskRepository.findById(task.id())
                .orElseThrow(() -> new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在"));
        taskEntity.accept(acceptedAt);
        rndTaskRepository.save(taskEntity);
    }

    private void persistExperimentDraft(ExperimentForm draft) {
        if (experimentFormRepository == null || experimentMaterialRepository == null) {
            return;
        }
        experimentFormRepository.save(new ExperimentFormEntity(
                draft.id(),
                draft.taskId(),
                draft.projectId(),
                draft.versionId(),
                draft.sampleNo(),
                draft.productName(),
                draft.versionCode(),
                draft.status().name(),
                draft.operatorName(),
                draft.summary(),
                draft.savedAt(),
                draft.submittedAt()
        ));
        experimentMaterialRepository.deleteByExperimentFormId(draft.id());
        var materialEntities = new ArrayList<ExperimentMaterialEntity>();
        for (var material : draft.materials()) {
            materialEntities.add(new ExperimentMaterialEntity(
                    "%s-M%04d".formatted(draft.id(), material.sequence()),
                    draft.id(),
                    material.stage(),
                    material.sequence(),
                    material.materialCode(),
                    material.materialName(),
                    material.weightKg(),
                    material.utilizationRate(),
                    material.remark()
            ));
        }
        experimentMaterialRepository.saveAll(materialEntities);
    }

    private SampleVersion requiredVersion(String versionId) {
        var version = versions.get(versionId);
        if (version == null) {
            throw new BusinessException("SAMPLE_VERSION_NOT_FOUND", "样品版本不存在");
        }
        return version;
    }

    private ExperimentForm lockedExperimentForm(String versionId) {
        return experimentForms.values().stream()
                .filter(form -> form.versionId().equals(versionId))
                .filter(form -> form.status() == ExperimentFormStatus.LOCKED)
                .findFirst()
                .orElseThrow(() -> new BusinessException("SAMPLE_VERSION_NOT_READY_FOR_PRICING", "实验单锁定后才能生成核价文件"));
    }

    private void ensureReadyForShipment(String versionId) {
        var hasLockedExperimentForm = experimentForms.values().stream()
                .anyMatch(form -> form.versionId().equals(versionId) && form.status() == ExperimentFormStatus.LOCKED);
        if (!hasLockedExperimentForm) {
            throw new BusinessException("SAMPLE_VERSION_NOT_READY_FOR_SHIPMENT", "实验单锁定后才能寄样");
        }
    }

    private long nextPricingVersionNumber(String versionId) {
        return pricingFiles.values().stream()
                .filter(file -> file.versionId().equals(versionId))
                .count() + 1;
    }

    private TestAssignment pendingTestAssignment(String testAssignmentId, String testerName) {
        var assignment = testAssignments.get(testAssignmentId);
        if (assignment == null) {
            throw new BusinessException("TEST_ASSIGNMENT_NOT_FOUND", "内部测试任务不存在");
        }
        if (assignment.status() != TestAssignmentStatus.PENDING_TEST) {
            throw new BusinessException("TEST_ASSIGNMENT_STATUS_ILLEGAL", "当前测试任务状态不可确认");
        }
        if (!testerName.equals(assignment.testerName())) {
            throw new BusinessException("TEST_ASSIGNMENT_TESTER_MISMATCH", "只能由被配置的测试人员确认");
        }
        return assignment;
    }

    private TestRecord testRecord(TestAssignment assignment, String testerName, TestAssignmentStatus result, String comment) {
        var record = new TestRecord(
                "TREC-%04d".formatted(testRecordSequence++),
                assignment.id(),
                assignment.experimentFormId(),
                testerName,
                result,
                comment,
                now()
        );
        testRecords.put(record.id(), record);
        return record;
    }

    public record CreateSampleRequestCommand(
            String productName,
            String productType,
            String customerName,
            String specification,
            String creatorName
    ) {
    }

    public record SaveExperimentDraftCommand(
            String taskId,
            String operatorName,
            String summary,
            List<ExperimentMaterial> materials
    ) {
    }

    public record CreateShipmentCommand(
            String versionId,
            Integer quantity,
            String receiverName,
            String trackingNo,
            String remark
    ) {
    }

    public record SubmitCustomerFeedbackCommand(
            String shipmentId,
            String feedbackBy,
            CustomerFeedbackResult result,
            String comment
    ) {
    }
}
