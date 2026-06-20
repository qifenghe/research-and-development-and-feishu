package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.SampleAction;
import com.lhr.rnd.domain.SampleStatus;
import com.lhr.rnd.domain.SampleStatusMachine;
import com.lhr.rnd.domain.SampleVersionCode;
import com.lhr.rnd.model.CustomerFeedback;
import com.lhr.rnd.model.CustomerFeedbackResult;
import com.lhr.rnd.model.DashboardOverview;
import com.lhr.rnd.model.DashboardPricingFileItem;
import com.lhr.rnd.model.DashboardTaskItem;
import com.lhr.rnd.model.DetailAction;
import com.lhr.rnd.model.DetailFieldGroup;
import com.lhr.rnd.model.FinanceNotification;
import com.lhr.rnd.model.FinanceNotificationStatus;
import com.lhr.rnd.model.PagedResult;
import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.RndTaskDetailView;
import com.lhr.rnd.model.RndTaskStatus;
import com.lhr.rnd.model.ArchiveFileView;
import com.lhr.rnd.model.ExperimentForm;
import com.lhr.rnd.model.ExperimentFormStatus;
import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.PricingFileRecord;
import com.lhr.rnd.model.PricingFileDetailView;
import com.lhr.rnd.model.PricingFileStatus;
import com.lhr.rnd.model.SampleProject;
import com.lhr.rnd.model.SampleRequest;
import com.lhr.rnd.model.SampleVersion;
import com.lhr.rnd.model.ShipmentDetailView;
import com.lhr.rnd.model.ShipmentRecord;
import com.lhr.rnd.model.ShipmentStatus;
import com.lhr.rnd.model.StoppedSampleProjectView;
import com.lhr.rnd.model.TestAssignment;
import com.lhr.rnd.model.TestAssignmentStatus;
import com.lhr.rnd.model.TestRecord;
import com.lhr.rnd.persistence.entity.ArchiveFileEntity;
import com.lhr.rnd.persistence.entity.CustomerFeedbackEntity;
import com.lhr.rnd.persistence.entity.ExperimentFormEntity;
import com.lhr.rnd.persistence.entity.ExperimentMaterialEntity;
import com.lhr.rnd.persistence.entity.FinanceNotificationEntity;
import com.lhr.rnd.persistence.entity.PricingFileEntity;
import com.lhr.rnd.persistence.entity.RndTaskEntity;
import com.lhr.rnd.persistence.entity.SampleProjectEntity;
import com.lhr.rnd.persistence.entity.SampleRequestEntity;
import com.lhr.rnd.persistence.entity.SampleVersionEntity;
import com.lhr.rnd.persistence.entity.ShipmentRecordEntity;
import com.lhr.rnd.persistence.entity.TestAssignmentEntity;
import com.lhr.rnd.persistence.entity.TestRecordEntity;
import com.lhr.rnd.persistence.repository.ArchiveFileRepository;
import com.lhr.rnd.persistence.repository.CustomerFeedbackRepository;
import com.lhr.rnd.persistence.repository.ExperimentFormRepository;
import com.lhr.rnd.persistence.repository.ExperimentMaterialRepository;
import com.lhr.rnd.persistence.repository.FinanceNotificationRepository;
import com.lhr.rnd.persistence.repository.PricingFileRepository;
import com.lhr.rnd.persistence.repository.RndTaskRepository;
import com.lhr.rnd.persistence.repository.SampleProjectRepository;
import com.lhr.rnd.persistence.repository.SampleRequestRepository;
import com.lhr.rnd.persistence.repository.SampleVersionRepository;
import com.lhr.rnd.persistence.repository.ShipmentRecordRepository;
import com.lhr.rnd.persistence.repository.TestAssignmentRepository;
import com.lhr.rnd.persistence.repository.TestRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Set;
import java.util.UUID;

@Service
public class SampleWorkflowService {
    private static final int MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_ATTACHMENT_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final Clock clock;
    private final PricingFileService pricingFileService = new PricingFileService();
    private final LocalArchiveStorageService archiveStorageService;
    private final FeishuIntegrationService feishuIntegrationService;
    private final WorkflowDrivenSampleStatusMachine workflowStatusMachine;
    private final SampleRequestRepository sampleRequestRepository;
    private final SampleProjectRepository sampleProjectRepository;
    private final SampleVersionRepository sampleVersionRepository;
    private final RndTaskRepository rndTaskRepository;
    private final ExperimentFormRepository experimentFormRepository;
    private final ExperimentMaterialRepository experimentMaterialRepository;
    private final TestAssignmentRepository testAssignmentRepository;
    private final TestRecordRepository testRecordRepository;
    private final ShipmentRecordRepository shipmentRecordRepository;
    private final CustomerFeedbackRepository customerFeedbackRepository;
    private final PricingFileRepository pricingFileRepository;
    private final FinanceNotificationRepository financeNotificationRepository;
    private final ArchiveFileRepository archiveFileRepository;
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
        this(Clock.systemDefaultZone(), new LocalArchiveStorageService(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    SampleWorkflowService(Clock clock) {
        this(clock, new LocalArchiveStorageService(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Autowired
    public SampleWorkflowService(
            LocalArchiveStorageService archiveStorageService,
            FeishuIntegrationService feishuIntegrationService,
            WorkflowDrivenSampleStatusMachine workflowStatusMachine,
            SampleRequestRepository sampleRequestRepository,
            SampleProjectRepository sampleProjectRepository,
            SampleVersionRepository sampleVersionRepository,
            RndTaskRepository rndTaskRepository,
            ExperimentFormRepository experimentFormRepository,
            ExperimentMaterialRepository experimentMaterialRepository,
            TestAssignmentRepository testAssignmentRepository,
            TestRecordRepository testRecordRepository,
            ShipmentRecordRepository shipmentRecordRepository,
            CustomerFeedbackRepository customerFeedbackRepository,
            PricingFileRepository pricingFileRepository,
            FinanceNotificationRepository financeNotificationRepository,
            ArchiveFileRepository archiveFileRepository
    ) {
        this(
                Clock.systemDefaultZone(),
                archiveStorageService,
                feishuIntegrationService,
                workflowStatusMachine,
                sampleRequestRepository,
                sampleProjectRepository,
                sampleVersionRepository,
                rndTaskRepository,
                experimentFormRepository,
                experimentMaterialRepository,
                testAssignmentRepository,
                testRecordRepository,
                shipmentRecordRepository,
                customerFeedbackRepository,
                pricingFileRepository,
                financeNotificationRepository,
                archiveFileRepository
        );
    }

    private SampleWorkflowService(
            Clock clock,
            LocalArchiveStorageService archiveStorageService,
            FeishuIntegrationService feishuIntegrationService,
            WorkflowDrivenSampleStatusMachine workflowStatusMachine,
            SampleRequestRepository sampleRequestRepository,
            SampleProjectRepository sampleProjectRepository,
            SampleVersionRepository sampleVersionRepository,
            RndTaskRepository rndTaskRepository,
            ExperimentFormRepository experimentFormRepository,
            ExperimentMaterialRepository experimentMaterialRepository,
            TestAssignmentRepository testAssignmentRepository,
            TestRecordRepository testRecordRepository,
            ShipmentRecordRepository shipmentRecordRepository,
            CustomerFeedbackRepository customerFeedbackRepository,
            PricingFileRepository pricingFileRepository,
            FinanceNotificationRepository financeNotificationRepository,
            ArchiveFileRepository archiveFileRepository
    ) {
        this.clock = clock;
        this.archiveStorageService = archiveStorageService;
        this.feishuIntegrationService = feishuIntegrationService;
        this.workflowStatusMachine = workflowStatusMachine;
        this.sampleRequestRepository = sampleRequestRepository;
        this.sampleProjectRepository = sampleProjectRepository;
        this.sampleVersionRepository = sampleVersionRepository;
        this.rndTaskRepository = rndTaskRepository;
        this.experimentFormRepository = experimentFormRepository;
        this.experimentMaterialRepository = experimentMaterialRepository;
        this.testAssignmentRepository = testAssignmentRepository;
        this.testRecordRepository = testRecordRepository;
        this.shipmentRecordRepository = shipmentRecordRepository;
        this.customerFeedbackRepository = customerFeedbackRepository;
        this.pricingFileRepository = pricingFileRepository;
        this.financeNotificationRepository = financeNotificationRepository;
        this.archiveFileRepository = archiveFileRepository;
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

        var projectStatus = transitionSampleStatus(request.status(), SampleAction.APPROVE_REQUEST);
        var approvedRequest = new SampleRequest(
                request.id(),
                request.sampleNo(),
                request.productName(),
                request.productType(),
                request.customerName(),
                request.specification(),
                request.creatorName(),
                projectStatus,
                request.createdAt()
        );
        var project = new SampleProject(
                "PROJ-%04d".formatted(projects.size() + 1),
                request.sampleNo(),
                request.productName(),
                request.productType(),
                request.customerName(),
                request.specification(),
                projectStatus,
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

        requests.put(approvedRequest.id(), approvedRequest);
        projects.put(project.id(), project);
        versions.put(version.id(), version);
        tasks.put(task.id(), task);
        persistApprovedWorkflow(approvedRequest, project, version, task);
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
        var nextStatus = taskStatusAfter(SampleStatus.PENDING_ASSIGNMENT, SampleAction.ASSIGN_TASK);
        var assigned = task.assign(assigneeName, dueDate, now()).withStatus(nextStatus);
        tasks.put(taskId, assigned);
        persistAssignedTask(assigned);
        notifyTaskAssigned(assigned);
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
        var nextStatus = taskStatusAfter(SampleStatus.PENDING_ACCEPTANCE, SampleAction.ACCEPT_TASK);
        var accepted = task.accept(acceptedAt).withStatus(nextStatus);
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

    @Transactional
    public synchronized SubmitExperimentForTestResult submitExperimentForTest(String experimentFormId, String testerName) {
        var form = experimentForms.get(experimentFormId);
        if (form == null) {
            throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
        }
        if (form.status() != ExperimentFormStatus.DRAFT) {
            throw new BusinessException("EXPERIMENT_FORM_STATUS_ILLEGAL", "只有草稿实验单可以提交测试");
        }
        var nextTaskStatus = taskStatusAfter(SampleStatus.SAMPLING, SampleAction.SUBMIT_EXPERIMENT);
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
                    nextTaskStatus,
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
        persistSubmittedExperiment(submitted, assignment);
        return new SubmitExperimentForTestResult(submitted, assignment);
    }

    @Transactional
    public synchronized PassInternalTestResult passInternalTest(String testAssignmentId, String testerName, String comment) {
        var assignment = pendingTestAssignment(testAssignmentId, testerName);
        var form = experimentForms.get(assignment.experimentFormId());
        if (form == null) {
            throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
        }
        ensureWorkflowAllows(SampleStatus.PENDING_TEST, SampleAction.TEST_PASS);
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
        persistPassedInternalTest(locked, passed, record, completedTask);
        return new PassInternalTestResult(locked, passed, record, completedTask);
    }

    @Transactional
    public synchronized FailInternalTestResult failInternalTestForResample(String testAssignmentId, String testerName, String comment) {
        var assignment = pendingTestAssignment(testAssignmentId, testerName);
        ensureWorkflowAllows(SampleStatus.PENDING_TEST, SampleAction.TEST_FAIL_RESAMPLE);
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
        var nextTaskStatus = taskStatusAfter(SampleStatus.RESAMPLING_REQUIRED, SampleAction.CREATE_NEXT_VERSION);
        var nextTask = new RndTask(
                "TASK-%04d".formatted(taskSequence++),
                nextVersion.projectId(),
                nextVersion.id(),
                nextVersion.sampleNo(),
                nextVersion.productName(),
                nextVersion.versionCode(),
                nextTaskStatus,
                previousTask == null ? null : previousTask.assigneeName(),
                previousTask == null ? null : previousTask.dueDate(),
                now(),
                null
        );
        tasks.put(nextTask.id(), nextTask);
        persistFailedInternalTest(failed, record, nextVersion, previousTask, nextTask);
        return new FailInternalTestResult(failed, record, nextVersion, nextTask);
    }

    public synchronized List<SampleRequest> requests() {
        return requests(null, null);
    }

    public synchronized List<SampleRequest> requests(String status, String keyword) {
        return requests.values().stream()
                .filter(request -> matchesStatus(status, request.status().name()))
                .filter(request -> matchesKeyword(
                        keyword,
                        request.sampleNo(),
                        request.productName(),
                        request.productType(),
                        request.customerName(),
                        request.specification(),
                        request.creatorName(),
                        request.status().name()
                ))
                .toList();
    }

    public synchronized PagedResult<SampleRequest> requests(String status, String keyword, Integer page, Integer size, String sort) {
        return paginate(
                requests(status, keyword),
                page,
                size,
                sort,
                SampleRequest::createdAt
        );
    }

    public synchronized List<RndTask> tasks(String status, String keyword) {
        return tasks.values().stream()
                .filter(task -> matchesStatus(status, task.status().name()))
                .filter(task -> matchesKeyword(
                        keyword,
                        task.id(),
                        task.sampleNo(),
                        task.productName(),
                        task.versionCode(),
                        task.assigneeName(),
                        task.status().name()
                ))
                .toList();
    }

    public synchronized PagedResult<RndTask> tasks(String status, String keyword, Integer page, Integer size, String sort) {
        return paginate(
                tasks(status, keyword),
                page,
                size,
                sort,
                RndTask::createdAt
        );
    }

    public synchronized RndTaskDetailView rndTaskDetail(String taskId, String role) {
        var task = requiredTask(taskId);
        var version = requiredVersion(task.versionId());
        var currentExperimentForm = currentExperimentForm(taskId);
        var currentTestAssignment = currentTestAssignment(taskId);
        return new RndTaskDetailView(
                task,
                version,
                currentExperimentForm,
                currentTestAssignment,
                detailFieldGroups(currentExperimentForm, currentTestAssignment),
                detailActions(task, currentExperimentForm, currentTestAssignment, role)
        );
    }

    public synchronized List<PricingFileRecord> pricingFiles(String status, String keyword) {
        return pricingFiles.values().stream()
                .filter(pricingFile -> matchesStatus(status, pricingFile.status().name()))
                .filter(pricingFile -> matchesKeyword(
                        keyword,
                        pricingFile.id(),
                        pricingFile.sampleNo(),
                        pricingFile.productName(),
                        pricingFile.versionCode(),
                        pricingFile.pricingVersion(),
                        pricingFile.fileName(),
                        pricingFile.status().name()
                ))
                .toList();
    }

    public synchronized PagedResult<PricingFileRecord> pricingFiles(String status, String keyword, Integer page, Integer size, String sort) {
        return paginate(
                pricingFiles(status, keyword),
                page,
                size,
                sort,
                PricingFileRecord::generatedAt
        );
    }

    public synchronized ShipmentDetailView shipmentDetail(String shipmentId, String role) {
        var shipment = requiredShipment(shipmentId);
        var version = requiredVersion(shipment.versionId());
        var feedback = currentCustomerFeedback(shipment.id());
        return new ShipmentDetailView(
                shipment,
                version,
                feedback,
                shipmentFieldGroups(feedback),
                shipmentActions(shipment, role)
        );
    }

    public synchronized PricingFileDetailView pricingFileDetail(String pricingFileId, String role) {
        var pricingFile = requiredPricingFile(pricingFileId);
        var version = requiredVersion(pricingFile.versionId());
        var financeNotification = currentFinanceNotification(pricingFile.id());
        return new PricingFileDetailView(
                pricingFile,
                version,
                financeNotification,
                pricingFileFieldGroups(financeNotification),
                pricingFileActions(pricingFile, role)
        );
    }

    public synchronized DashboardOverview dashboardOverview() {
        var pendingReviewCount = requests.values().stream()
                .filter(request -> request.status() == SampleStatus.PENDING_REVIEW)
                .count();
        var pendingAssignmentCount = tasks.values().stream()
                .filter(task -> task.status() == RndTaskStatus.PENDING_ASSIGNMENT)
                .count();
        var pendingAcceptanceCount = tasks.values().stream()
                .filter(task -> task.status() == RndTaskStatus.PENDING_ACCEPTANCE)
                .count();
        var samplingCount = tasks.values().stream()
                .filter(task -> task.status() == RndTaskStatus.SAMPLING)
                .count();
        var pendingTestCount = tasks.values().stream()
                .filter(task -> task.status() == RndTaskStatus.PENDING_TEST)
                .count();
        var completedSampleCount = tasks.values().stream()
                .filter(task -> task.status() == RndTaskStatus.COMPLETED)
                .count();
        var pendingPricingCount = pricingFiles.values().stream()
                .filter(pricingFile -> pricingFile.status() == PricingFileStatus.GENERATED)
                .count();
        var financeNotifiedCount = pricingFiles.values().stream()
                .filter(pricingFile -> pricingFile.status() == PricingFileStatus.FINANCE_NOTIFIED)
                .count();
        var stoppedCount = projects.values().stream()
                .filter(project -> project.status() == SampleStatus.STOPPED)
                .count();
        var recentTasks = tasks.values().stream()
                .filter(task -> task.status() != RndTaskStatus.COMPLETED)
                .sorted(Comparator.comparing(RndTask::createdAt).reversed())
                .limit(3)
                .map(this::dashboardTaskItem)
                .toList();
        var pendingPricingFiles = pricingFiles.values().stream()
                .filter(pricingFile -> pricingFile.status() == PricingFileStatus.GENERATED)
                .sorted(Comparator.comparing(PricingFileRecord::generatedAt).reversed())
                .limit(5)
                .map(this::dashboardPricingFileItem)
                .toList();

        return new DashboardOverview(
                pendingReviewCount,
                pendingAssignmentCount,
                pendingAcceptanceCount,
                samplingCount,
                pendingTestCount,
                completedSampleCount,
                pendingPricingCount,
                financeNotifiedCount,
                stoppedCount,
                recentTasks,
                pendingPricingFiles
        );
    }

    public synchronized List<StoppedSampleProjectView> stoppedProjects() {
        return projects.values().stream()
                .filter(project -> project.status() == SampleStatus.STOPPED)
                .map(this::stoppedProjectView)
                .toList();
    }

    private DashboardTaskItem dashboardTaskItem(RndTask task) {
        return new DashboardTaskItem(
                task.id(),
                task.sampleNo(),
                task.productName(),
                task.versionCode(),
                task.status().name(),
                task.assigneeName(),
                task.dueDate(),
                task.createdAt()
        );
    }

    private DashboardPricingFileItem dashboardPricingFileItem(PricingFileRecord pricingFile) {
        return new DashboardPricingFileItem(
                pricingFile.id(),
                pricingFile.sampleNo(),
                pricingFile.productName(),
                pricingFile.versionCode(),
                pricingFile.pricingVersion(),
                pricingFile.status().name(),
                pricingFile.generatedAt()
        );
    }

    private boolean matchesStatus(String expectedStatus, String actualStatus) {
        return expectedStatus == null || expectedStatus.isBlank() || expectedStatus.equals(actualStatus);
    }

    private boolean matchesKeyword(String keyword, String... values) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        var normalizedKeyword = keyword.trim().toLowerCase();
        for (String value : values) {
            if (value != null && value.toLowerCase().contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private RndTask requiredTask(String taskId) {
        var task = tasks.get(taskId);
        if (task == null) {
            throw new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在");
        }
        return task;
    }

    private ShipmentRecord requiredShipment(String shipmentId) {
        var shipment = shipments.get(shipmentId);
        if (shipment == null) {
            throw new BusinessException("SHIPMENT_NOT_FOUND", "寄样记录不存在");
        }
        return shipment;
    }

    private PricingFileRecord requiredPricingFile(String pricingFileId) {
        var pricingFile = pricingFiles.get(pricingFileId);
        if (pricingFile == null) {
            throw new BusinessException("PRICING_FILE_NOT_FOUND", "核价文件不存在");
        }
        return pricingFile;
    }

    private ExperimentForm currentExperimentForm(String taskId) {
        return experimentForms.values().stream()
                .filter(form -> form.taskId().equals(taskId))
                .max(Comparator.comparing(ExperimentForm::savedAt))
                .orElse(null);
    }

    private TestAssignment currentTestAssignment(String taskId) {
        return testAssignments.values().stream()
                .filter(assignment -> assignment.taskId().equals(taskId))
                .max(Comparator.comparing(TestAssignment::assignedAt))
                .orElse(null);
    }

    private CustomerFeedback currentCustomerFeedback(String shipmentId) {
        return customerFeedbacks.values().stream()
                .filter(feedback -> feedback.shipmentId().equals(shipmentId))
                .max(Comparator.comparing(CustomerFeedback::feedbackAt))
                .orElse(null);
    }

    private FinanceNotification currentFinanceNotification(String pricingFileId) {
        return financeNotifications.values().stream()
                .filter(notification -> notification.pricingFileId().equals(pricingFileId))
                .max(Comparator.comparing(FinanceNotification::notifiedAt))
                .orElse(null);
    }

    private List<DetailFieldGroup> detailFieldGroups(
            ExperimentForm currentExperimentForm,
            TestAssignment currentTestAssignment
    ) {
        var groups = new ArrayList<DetailFieldGroup>();
        groups.add(new DetailFieldGroup("基础信息", List.of(
                "sampleNo",
                "productName",
                "versionCode"
        )));
        groups.add(new DetailFieldGroup("任务信息", List.of(
                "status",
                "assigneeName",
                "dueDate",
                "createdAt",
                "assignedAt"
        )));
        if (currentExperimentForm != null) {
            groups.add(new DetailFieldGroup("实验单", List.of(
                    "currentExperimentForm.id",
                    "currentExperimentForm.status",
                    "currentExperimentForm.operatorName",
                    "currentExperimentForm.summary",
                    "currentExperimentForm.materials"
            )));
        }
        if (currentTestAssignment != null) {
            groups.add(new DetailFieldGroup("内部测试", List.of(
                    "currentTestAssignment.id",
                    "currentTestAssignment.testerName",
                    "currentTestAssignment.status",
                    "currentTestAssignment.assignedAt"
            )));
        }
        return groups;
    }

    private List<DetailAction> detailActions(
            RndTask task,
            ExperimentForm currentExperimentForm,
            TestAssignment currentTestAssignment,
            String role
    ) {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return switch (task.status()) {
            case PENDING_ASSIGNMENT -> hasRole(role, "RND_DIRECTOR")
                    ? List.of(action("ASSIGN_TASK", "分发任务", "POST", "/api/v1/rnd-tasks/" + task.id() + "/assign", true))
                    : List.of();
            case PENDING_ACCEPTANCE -> hasRole(role, "RND_ENGINEER", "RND")
                    ? List.of(action("ACCEPT_TASK", "接受任务", "POST", "/api/v1/rnd-tasks/" + task.id() + "/accept", true))
                    : List.of();
            case SAMPLING -> samplingActions(task, currentExperimentForm, role);
            case PENDING_TEST -> testActions(currentTestAssignment, role);
            case COMPLETED -> completedActions(task, role);
        };
    }

    private List<DetailAction> samplingActions(RndTask task, ExperimentForm currentExperimentForm, String role) {
        if (!hasRole(role, "RND_ENGINEER", "RND")) {
            return List.of();
        }
        var actions = new ArrayList<DetailAction>();
        actions.add(action(
                "SAVE_EXPERIMENT_DRAFT",
                "保存实验单",
                "POST",
                "/api/v1/rnd-tasks/" + task.id() + "/experiment-form/draft",
                true
        ));
        if (currentExperimentForm != null && currentExperimentForm.status() == ExperimentFormStatus.DRAFT) {
            actions.add(action(
                    "SUBMIT_EXPERIMENT_TEST",
                    "提交内部测试",
                    "POST",
                    "/api/v1/experiment-forms/" + currentExperimentForm.id() + "/submit-test",
                    true
            ));
        }
        return actions;
    }

    private List<DetailAction> testActions(TestAssignment currentTestAssignment, String role) {
        if (!hasRole(role, "TESTER", "QA_TESTER") || currentTestAssignment == null
                || currentTestAssignment.status() != TestAssignmentStatus.PENDING_TEST) {
            return List.of();
        }
        return List.of(
                action("PASS_INTERNAL_TEST", "测试通过并锁版", "POST", "/api/v1/test-assignments/" + currentTestAssignment.id() + "/pass", true),
                action("FAIL_RESAMPLE", "不通过复打样", "POST", "/api/v1/test-assignments/" + currentTestAssignment.id() + "/fail-resample", false)
        );
    }

    private List<DetailAction> completedActions(RndTask task, String role) {
        if (!hasRole(role, "RND_ASSISTANT")) {
            return List.of();
        }
        return List.of(
                action("CREATE_SHIPMENT", "登记寄样", "POST", "/api/v1/sample-versions/" + task.versionId() + "/shipments", true),
                action("GENERATE_PRICING_FILE", "生成核价文件", "POST", "/api/v1/sample-versions/" + task.versionId() + "/pricing-files", false)
        );
    }

    private List<DetailFieldGroup> shipmentFieldGroups(CustomerFeedback feedback) {
        var groups = new ArrayList<DetailFieldGroup>();
        groups.add(new DetailFieldGroup("寄样信息", List.of(
                "shipment.id",
                "shipment.sampleNo",
                "shipment.productName",
                "shipment.versionCode",
                "shipment.quantity",
                "shipment.receiverName",
                "shipment.trackingNo",
                "shipment.status",
                "shipment.shippedAt"
        )));
        if (feedback != null) {
            groups.add(new DetailFieldGroup("客户反馈", List.of(
                    "customerFeedback.feedbackBy",
                    "customerFeedback.result",
                    "customerFeedback.comment",
                    "customerFeedback.feedbackAt"
            )));
        }
        return groups;
    }

    private List<DetailFieldGroup> pricingFileFieldGroups(FinanceNotification financeNotification) {
        var groups = new ArrayList<DetailFieldGroup>();
        groups.add(new DetailFieldGroup("核价文件", List.of(
                "pricingFile.id",
                "pricingFile.sampleNo",
                "pricingFile.productName",
                "pricingFile.versionCode",
                "pricingFile.pricingVersion",
                "pricingFile.fileName",
                "pricingFile.status",
                "pricingFile.generatedAt"
        )));
        if (financeNotification != null) {
            groups.add(new DetailFieldGroup("财务通知", List.of(
                    "financeNotification.recipientName",
                    "financeNotification.remark",
                    "financeNotification.status",
                    "financeNotification.notifiedAt"
            )));
        }
        return groups;
    }

    private List<DetailAction> shipmentActions(ShipmentRecord shipment, String role) {
        if (!hasRole(role, "RND_ASSISTANT")) {
            return List.of();
        }
        if (shipment.status() == ShipmentStatus.SHIPPED) {
            return List.of(
                    action("CUSTOMER_FEEDBACK_PASS", "客户通过", "POST", "/api/v1/shipments/" + shipment.id() + "/feedback", true),
                    action("CUSTOMER_FEEDBACK_RESAMPLE", "客户不通过复打样", "POST", "/api/v1/shipments/" + shipment.id() + "/feedback", false),
                    action("CUSTOMER_FEEDBACK_STOP", "停止打样", "POST", "/api/v1/shipments/" + shipment.id() + "/feedback", false)
            );
        }
        if (shipment.status() == ShipmentStatus.FEEDBACK_PASSED) {
            return List.of(action(
                    "GENERATE_PRICING_FILE",
                    "生成核价文件",
                    "POST",
                    "/api/v1/sample-versions/" + shipment.versionId() + "/pricing-files",
                    true
            ));
        }
        return List.of();
    }

    private List<DetailAction> pricingFileActions(PricingFileRecord pricingFile, String role) {
        if (!hasRole(role, "RND_ASSISTANT", "FINANCE")) {
            return List.of();
        }
        var actions = new ArrayList<DetailAction>();
        actions.add(action(
                "DOWNLOAD_PRICING_FILE",
                "下载核价文件",
                "GET",
                "/api/v1/pricing-files/" + pricingFile.id() + "/download",
                true
        ));
        if (hasRole(role, "RND_ASSISTANT") && pricingFile.status() == PricingFileStatus.GENERATED) {
            actions.add(action(
                    "NOTIFY_FINANCE",
                    "通知财务核价",
                    "POST",
                    "/api/v1/pricing-files/" + pricingFile.id() + "/notify-finance",
                    true
            ));
        }
        return actions;
    }

    private DetailAction action(String code, String label, String httpMethod, String endpoint, boolean primary) {
        return new DetailAction(code, label, httpMethod, endpoint, primary);
    }

    private boolean hasRole(String role, String... allowedRoles) {
        for (String allowedRole : allowedRoles) {
            if (allowedRole.equals(role)) {
                return true;
            }
        }
        return false;
    }

    private <T> PagedResult<T> paginate(
            List<T> source,
            Integer page,
            Integer size,
            String sort,
            Function<T, LocalDateTime> timeExtractor
    ) {
        var resolvedPage = page == null || page < 0 ? 0 : page;
        var resolvedSize = size == null || size <= 0 ? 20 : Math.min(size, 200);
        var descending = sort == null || sort.isBlank() || !sort.toLowerCase().endsWith(",asc");
        Comparator<T> comparator = Comparator.comparing(timeExtractor);
        if (descending) {
            comparator = comparator.reversed();
        }
        var sorted = source.stream()
                .sorted(comparator)
                .toList();
        var total = sorted.size();
        var fromIndex = Math.min(resolvedPage * resolvedSize, total);
        var toIndex = Math.min(fromIndex + resolvedSize, total);
        var totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / resolvedSize);
        return new PagedResult<>(
                sorted.subList(fromIndex, toIndex),
                total,
                resolvedPage,
                resolvedSize,
                totalPages
        );
    }

    @Transactional
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
        persistShipment(shipment);
        return shipment;
    }

    @Transactional
    public synchronized ShipmentFeedbackResult submitCustomerFeedback(SubmitCustomerFeedbackCommand command) {
        var shipment = shipments.get(command.shipmentId());
        if (shipment == null) {
            throw new BusinessException("SHIPMENT_NOT_FOUND", "寄样记录不存在");
        }
        if (shipment.status() != ShipmentStatus.SHIPPED) {
            throw new BusinessException("SHIPMENT_STATUS_ILLEGAL", "当前寄样状态不可反馈");
        }
        ensureWorkflowAllows(SampleStatus.SAMPLE_COMPLETED, customerFeedbackAction(command.result()));

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
        var resampleTask = command.result() == CustomerFeedbackResult.FAILED_RESAMPLE
                ? createCustomerResampleTask(shipment.versionId())
                : null;
        var stoppedProject = command.result() == CustomerFeedbackResult.STOPPED
                ? updateProjectStatusForVersion(shipment.versionId(), SampleStatus.STOPPED)
                : null;
        persistCustomerFeedback(updatedShipment, feedback);
        if (resampleTask != null) {
            persistCustomerResampleTask(resampleTask.nextVersion(), resampleTask.nextTask());
        }
        if (stoppedProject != null) {
            persistSampleProjectStatus(stoppedProject);
        }
        return new ShipmentFeedbackResult(updatedShipment, feedback);
    }

    @Transactional
    public synchronized PricingFileRecord generatePricingFile(String versionId) {
        var version = requiredVersion(versionId);
        var lockedForm = lockedExperimentForm(versionId);
        ensureWorkflowAllows(SampleStatus.SAMPLE_COMPLETED, SampleAction.REQUEST_PRICING);
        ensurePricingArchiveAllowed();
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
        persistPricingFile(record, generated.content());
        return record;
    }

    @Transactional
    public synchronized NotifyFinanceResult notifyFinance(String pricingFileId, String recipientName, String remark) {
        var pricingFile = pricingFiles.get(pricingFileId);
        if (pricingFile == null) {
            throw new BusinessException("PRICING_FILE_NOT_FOUND", "核价文件不存在");
        }
        ensureWorkflowAllows(SampleStatus.PRICING_FILE_GENERATED, SampleAction.NOTIFY_FINANCE);
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
        persistFinanceNotification(notifiedPricingFile, notification);
        return new NotifyFinanceResult(notifiedPricingFile, notification);
    }

    public synchronized List<ArchiveFileView> archiveFiles(String versionId) {
        if (archiveFileRepository == null) {
            return List.of();
        }
        return archiveFileRepository.findByVersionIdOrderByArchivedAtDesc(versionId).stream()
                .map(ArchiveFileEntity::toView)
                .toList();
    }

    public synchronized ArchiveFileDownload downloadArchiveFile(String archiveFileId) {
        if (archiveFileRepository == null) {
            throw new BusinessException("ARCHIVE_FILE_NOT_FOUND", "归档文件不存在");
        }
        var archiveFile = archiveFileRepository.findById(archiveFileId)
                .orElseThrow(() -> new BusinessException("ARCHIVE_FILE_NOT_FOUND", "归档文件不存在"));
        return new ArchiveFileDownload(
                archiveFile.getFileName(),
                archiveStorageService.read(archiveFile.getFilePath())
        );
    }

    @Transactional
    public synchronized ArchiveFileView archiveExperimentAttachment(
            String experimentFormId,
            String fileName,
            byte[] content,
            String category,
            String uploadedBy,
            String remark,
            String contentType
    ) {
        if (archiveFileRepository == null) {
            throw new BusinessException("ARCHIVE_FILE_REPOSITORY_NOT_READY", "归档仓库未初始化");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException("ARCHIVE_FILE_EMPTY", "上传文件不能为空");
        }
        if (content.length > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new BusinessException("ARCHIVE_FILE_TOO_LARGE", "上传文件不能超过10MB");
        }
        var normalizedContentType = normalizeOptional(contentType);
        if (normalizedContentType == null || !ALLOWED_ATTACHMENT_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new BusinessException("ARCHIVE_FILE_TYPE_NOT_ALLOWED", "上传文件类型不允许");
        }
        var form = experimentFormArchiveContext(experimentFormId);
        var cleanFileName = cleanArchiveFileName(fileName);
        var archiveId = "ARCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27);
        var relativePath = "%s/%s/实验附件/%s/%s".formatted(
                form.sampleNo(),
                form.versionCode(),
                archiveId,
                cleanFileName
        );
        archiveStorageService.store(relativePath, content);
        var archiveFile = new ArchiveFileEntity(
                archiveId,
                "EXPERIMENT_ATTACHMENT",
                form.id(),
                form.versionId(),
                cleanFileName,
                relativePath,
                null,
                normalizeOptional(category),
                normalizeOptional(uploadedBy),
                normalizeOptional(remark),
                normalizedContentType,
                (long) content.length,
                "ARCHIVED",
                now()
        );
        archiveFileRepository.save(archiveFile);
        return archiveFile.toView();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private SampleStatus transitionSampleStatus(SampleStatus current, SampleAction action) {
        try {
            if (workflowStatusMachine != null) {
                return workflowStatusMachine.transition(current, action);
            }
            return new SampleStatusMachine().transition(current, action);
        } catch (IllegalStateException exception) {
            throw new BusinessException("SAMPLE_STATUS_TRANSITION_ILLEGAL", exception.getMessage());
        }
    }

    private void ensureWorkflowAllows(SampleStatus current, SampleAction action) {
        transitionSampleStatus(current, action);
    }

    private void ensurePricingArchiveAllowed() {
        if (archiveFileRepository != null) {
            ensureWorkflowAllows(SampleStatus.FINANCE_NOTIFIED, SampleAction.ARCHIVE);
        }
    }

    private SampleAction customerFeedbackAction(CustomerFeedbackResult result) {
        return switch (result) {
            case PASSED -> SampleAction.CUSTOMER_FEEDBACK_PASS;
            case FAILED_RESAMPLE -> SampleAction.CUSTOMER_FEEDBACK_RESAMPLE;
            case STOPPED -> SampleAction.CUSTOMER_FEEDBACK_STOP;
        };
    }

    private CustomerResampleTask createCustomerResampleTask(String currentVersionId) {
        var currentVersion = requiredVersion(currentVersionId);
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

        var previousTask = taskByVersionId(currentVersion.id());
        var nextTaskStatus = taskStatusAfter(SampleStatus.RESAMPLING_REQUIRED, SampleAction.CREATE_NEXT_VERSION);
        var nextTask = new RndTask(
                "TASK-%04d".formatted(taskSequence++),
                nextVersion.projectId(),
                nextVersion.id(),
                nextVersion.sampleNo(),
                nextVersion.productName(),
                nextVersion.versionCode(),
                nextTaskStatus,
                previousTask == null ? null : previousTask.assigneeName(),
                previousTask == null ? null : previousTask.dueDate(),
                now(),
                null
        );
        tasks.put(nextTask.id(), nextTask);
        return new CustomerResampleTask(nextVersion, nextTask);
    }

    private RndTask taskByVersionId(String versionId) {
        return tasks.values().stream()
                .filter(task -> task.versionId().equals(versionId))
                .findFirst()
                .orElse(null);
    }

    private SampleProject updateProjectStatusForVersion(String versionId, SampleStatus status) {
        var version = requiredVersion(versionId);
        var project = projects.get(version.projectId());
        if (project == null) {
            throw new BusinessException("SAMPLE_PROJECT_NOT_FOUND", "样品项目不存在");
        }
        var updatedProject = new SampleProject(
                project.id(),
                project.sampleNo(),
                project.productName(),
                project.productType(),
                project.customerName(),
                project.specification(),
                status,
                project.createdAt()
        );
        projects.put(updatedProject.id(), updatedProject);
        return updatedProject;
    }

    private StoppedSampleProjectView stoppedProjectView(SampleProject project) {
        var projectVersions = versions.values().stream()
                .filter(version -> version.projectId().equals(project.id()))
                .toList();
        var lastVersionCode = projectVersions.stream()
                .max(Comparator.comparing(version -> version.versionNumber() == null ? -1 : version.versionNumber()))
                .map(SampleVersion::versionCode)
                .orElse(null);
        var stoppedFeedback = customerFeedbacks.values().stream()
                .filter(feedback -> feedback.result() == CustomerFeedbackResult.STOPPED)
                .filter(feedback -> shipmentBelongsToAnyVersion(feedback.shipmentId(), projectVersions))
                .max(Comparator.comparing(CustomerFeedback::feedbackAt));

        return new StoppedSampleProjectView(
                project.id(),
                project.sampleNo(),
                project.productName(),
                project.productType(),
                project.customerName(),
                project.specification(),
                project.status().name(),
                lastVersionCode,
                stoppedFeedback.map(CustomerFeedback::feedbackBy).orElse(null),
                stoppedFeedback.map(CustomerFeedback::comment).orElse(null),
                stoppedFeedback.map(CustomerFeedback::feedbackAt).orElse(null)
        );
    }

    private boolean shipmentBelongsToAnyVersion(String shipmentId, List<SampleVersion> projectVersions) {
        var shipment = shipments.get(shipmentId);
        if (shipment == null) {
            return false;
        }
        return projectVersions.stream().anyMatch(version -> version.id().equals(shipment.versionId()));
    }

    private RndTaskStatus taskStatusAfter(SampleStatus current, SampleAction action) {
        var nextStatus = transitionSampleStatus(current, action);
        try {
            return RndTaskStatus.valueOf(nextStatus.name());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "SAMPLE_STATUS_TRANSITION_ILLEGAL",
                    "流程配置目标状态不能用于研发任务: " + nextStatus
            );
        }
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
        persistRequest(request);
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

    private void notifyTaskAssigned(RndTask task) {
        if (feishuIntegrationService == null) {
            return;
        }
        feishuIntegrationService.createTaskAssignedNotification(task);
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

    private void persistSubmittedExperiment(ExperimentForm submitted, TestAssignment assignment) {
        if (experimentFormRepository == null || rndTaskRepository == null || testAssignmentRepository == null) {
            return;
        }
        var formEntity = experimentFormRepository.findById(submitted.id())
                .orElseThrow(() -> new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在"));
        formEntity.submit(submitted.submittedAt());
        experimentFormRepository.save(formEntity);

        var taskEntity = rndTaskRepository.findById(submitted.taskId())
                .orElseThrow(() -> new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在"));
        taskEntity.markPendingTest();
        rndTaskRepository.save(taskEntity);

        testAssignmentRepository.save(new TestAssignmentEntity(
                assignment.id(),
                assignment.experimentFormId(),
                assignment.taskId(),
                assignment.versionId(),
                assignment.testerName(),
                assignment.status().name(),
                assignment.assignedAt()
        ));
    }

    private void persistPassedInternalTest(
            ExperimentForm locked,
            TestAssignment passed,
            TestRecord record,
            RndTask completedTask
    ) {
        if (experimentFormRepository == null || testAssignmentRepository == null || testRecordRepository == null) {
            return;
        }
        var formEntity = experimentFormRepository.findById(locked.id())
                .orElseThrow(() -> new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在"));
        formEntity.lock();
        experimentFormRepository.save(formEntity);

        var assignmentEntity = testAssignmentRepository.findById(passed.id())
                .orElseThrow(() -> new BusinessException("TEST_ASSIGNMENT_NOT_FOUND", "内部测试任务不存在"));
        assignmentEntity.pass();
        testAssignmentRepository.save(assignmentEntity);

        testRecordRepository.save(new TestRecordEntity(
                record.id(),
                record.testAssignmentId(),
                record.experimentFormId(),
                record.testerName(),
                record.result().name(),
                record.comment(),
                record.testedAt()
        ));

        if (completedTask != null && rndTaskRepository != null) {
            var taskEntity = rndTaskRepository.findById(completedTask.id())
                    .orElseThrow(() -> new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在"));
            taskEntity.complete();
            rndTaskRepository.save(taskEntity);
        }
    }

    private void persistFailedInternalTest(
            TestAssignment failed,
            TestRecord record,
            SampleVersion nextVersion,
            RndTask previousTask,
            RndTask nextTask
    ) {
        if (testAssignmentRepository == null || testRecordRepository == null
                || sampleVersionRepository == null || rndTaskRepository == null) {
            return;
        }
        var assignmentEntity = testAssignmentRepository.findById(failed.id())
                .orElseThrow(() -> new BusinessException("TEST_ASSIGNMENT_NOT_FOUND", "内部测试任务不存在"));
        assignmentEntity.failForResample();
        testAssignmentRepository.save(assignmentEntity);

        testRecordRepository.save(new TestRecordEntity(
                record.id(),
                record.testAssignmentId(),
                record.experimentFormId(),
                record.testerName(),
                record.result().name(),
                record.comment(),
                record.testedAt()
        ));

        sampleVersionRepository.save(new SampleVersionEntity(
                nextVersion.id(),
                nextVersion.projectId(),
                nextVersion.sampleNo(),
                nextVersion.productName(),
                nextVersion.productType(),
                nextVersion.specification(),
                nextVersion.versionNo(),
                nextVersion.versionNumber(),
                nextVersion.versionCode(),
                nextVersion.ownerName(),
                nextVersion.authorName(),
                nextVersion.effectiveDate(),
                nextVersion.referenceOutputKg(),
                nextVersion.unitWeightKg(),
                nextVersion.createdAt()
        ));

        if (previousTask != null) {
            var previousTaskEntity = rndTaskRepository.findById(previousTask.id())
                    .orElseThrow(() -> new BusinessException("RND_TASK_NOT_FOUND", "研发任务不存在"));
            previousTaskEntity.complete();
            rndTaskRepository.save(previousTaskEntity);
        }

        rndTaskRepository.save(new RndTaskEntity(
                nextTask.id(),
                nextTask.projectId(),
                nextTask.versionId(),
                nextTask.sampleNo(),
                nextTask.productName(),
                nextTask.versionCode(),
                nextTask.status().name(),
                nextTask.assigneeName(),
                nextTask.dueDate(),
                nextTask.createdAt(),
                nextTask.assignedAt(),
                null
        ));
    }

    private void persistShipment(ShipmentRecord shipment) {
        if (shipmentRecordRepository == null) {
            return;
        }
        shipmentRecordRepository.save(new ShipmentRecordEntity(
                shipment.id(),
                shipment.versionId(),
                shipment.sampleNo(),
                shipment.productName(),
                shipment.versionCode(),
                shipment.quantity(),
                shipment.receiverName(),
                shipment.trackingNo(),
                shipment.remark(),
                shipment.status().name(),
                shipment.shippedAt()
        ));
    }

    private void persistCustomerFeedback(ShipmentRecord updatedShipment, CustomerFeedback feedback) {
        if (shipmentRecordRepository == null || customerFeedbackRepository == null) {
            return;
        }
        var shipmentEntity = shipmentRecordRepository.findById(updatedShipment.id())
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "寄样记录不存在"));
        shipmentEntity.updateStatus(updatedShipment.status().name());
        shipmentRecordRepository.save(shipmentEntity);

        customerFeedbackRepository.save(new CustomerFeedbackEntity(
                feedback.id(),
                feedback.shipmentId(),
                feedback.feedbackBy(),
                feedback.result().name(),
                feedback.comment(),
                feedback.feedbackAt()
        ));
    }

    private void persistCustomerResampleTask(SampleVersion nextVersion, RndTask nextTask) {
        if (sampleVersionRepository == null || rndTaskRepository == null) {
            return;
        }
        sampleVersionRepository.save(new SampleVersionEntity(
                nextVersion.id(),
                nextVersion.projectId(),
                nextVersion.sampleNo(),
                nextVersion.productName(),
                nextVersion.productType(),
                nextVersion.specification(),
                nextVersion.versionNo(),
                nextVersion.versionNumber(),
                nextVersion.versionCode(),
                nextVersion.ownerName(),
                nextVersion.authorName(),
                nextVersion.effectiveDate(),
                nextVersion.referenceOutputKg(),
                nextVersion.unitWeightKg(),
                nextVersion.createdAt()
        ));

        rndTaskRepository.save(new RndTaskEntity(
                nextTask.id(),
                nextTask.projectId(),
                nextTask.versionId(),
                nextTask.sampleNo(),
                nextTask.productName(),
                nextTask.versionCode(),
                nextTask.status().name(),
                nextTask.assigneeName(),
                nextTask.dueDate(),
                nextTask.createdAt(),
                nextTask.assignedAt(),
                null
        ));
    }

    private void persistSampleProjectStatus(SampleProject project) {
        if (sampleProjectRepository == null) {
            return;
        }
        var projectEntity = sampleProjectRepository.findById(project.id())
                .orElseThrow(() -> new BusinessException("SAMPLE_PROJECT_NOT_FOUND", "样品项目不存在"));
        projectEntity.updateStatus(project.status().name());
        sampleProjectRepository.save(projectEntity);
    }

    private void persistPricingFile(PricingFileRecord pricingFile, byte[] content) {
        if (pricingFileRepository == null) {
            return;
        }
        pricingFileRepository.save(new PricingFileEntity(
                pricingFile.id(),
                pricingFile.versionId(),
                pricingFile.sampleNo(),
                pricingFile.productName(),
                pricingFile.versionCode(),
                pricingFile.pricingVersion(),
                pricingFile.fileName(),
                pricingFile.status().name(),
                pricingFile.contentLength(),
                pricingFile.generatedAt()
        ));
        persistPricingArchive(pricingFile, content);
    }

    private void persistPricingArchive(PricingFileRecord pricingFile, byte[] content) {
        if (archiveFileRepository == null) {
            return;
        }
        var relativePath = "%s/%s/核价/%s".formatted(
                pricingFile.sampleNo(),
                pricingFile.versionCode(),
                pricingFile.fileName()
        );
        archiveStorageService.store(relativePath, content);
        archiveFileRepository.save(new ArchiveFileEntity(
                "ARCH-" + pricingFile.id(),
                "PRICING_FILE",
                pricingFile.id(),
                pricingFile.versionId(),
                pricingFile.fileName(),
                relativePath,
                null,
                "ARCHIVED",
                pricingFile.generatedAt()
        ));
    }

    private void persistFinanceNotification(PricingFileRecord notifiedPricingFile, FinanceNotification notification) {
        if (pricingFileRepository == null || financeNotificationRepository == null) {
            return;
        }
        var pricingFileEntity = pricingFileRepository.findById(notifiedPricingFile.id())
                .orElseThrow(() -> new BusinessException("PRICING_FILE_NOT_FOUND", "核价文件不存在"));
        pricingFileEntity.markFinanceNotified();
        pricingFileRepository.save(pricingFileEntity);

        financeNotificationRepository.save(new FinanceNotificationEntity(
                notification.id(),
                notification.pricingFileId(),
                notification.recipientName(),
                notification.remark(),
                notification.status().name(),
                notification.notifiedAt()
        ));
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

    private ExperimentFormArchiveContext experimentFormArchiveContext(String experimentFormId) {
        var form = experimentForms.get(experimentFormId);
        if (form != null) {
            return new ExperimentFormArchiveContext(
                    form.id(),
                    form.versionId(),
                    form.sampleNo(),
                    form.versionCode()
            );
        }
        if (experimentFormRepository == null) {
            throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
        }
        var entity = experimentFormRepository.findById(experimentFormId)
                .orElseThrow(() -> new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在"));
        return new ExperimentFormArchiveContext(
                entity.getId(),
                entity.getVersionId(),
                entity.getSampleNo(),
                entity.getVersionCode()
        );
    }

    private String cleanArchiveFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException("ARCHIVE_FILE_NAME_REQUIRED", "归档文件名不能为空");
        }
        var cleanFileName = fileName.replace("\\", "/")
                .replaceAll(".*/", "")
                .trim();
        if (cleanFileName.isBlank()) {
            throw new BusinessException("ARCHIVE_FILE_NAME_REQUIRED", "归档文件名不能为空");
        }
        return cleanFileName;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public record ArchiveFileDownload(
            String fileName,
            byte[] content
    ) {
    }

    private record ExperimentFormArchiveContext(
            String id,
            String versionId,
            String sampleNo,
            String versionCode
    ) {
    }

    private record CustomerResampleTask(
            SampleVersion nextVersion,
            RndTask nextTask
    ) {
    }
}
