package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.SampleStatus;
import com.lhr.rnd.domain.SampleVersionCode;
import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.RndTaskStatus;
import com.lhr.rnd.model.ExperimentForm;
import com.lhr.rnd.model.ExperimentFormStatus;
import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.SampleProject;
import com.lhr.rnd.model.SampleRequest;
import com.lhr.rnd.model.SampleVersion;
import com.lhr.rnd.model.TestAssignment;
import com.lhr.rnd.model.TestAssignmentStatus;
import org.springframework.stereotype.Service;

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
    private final Map<String, SampleRequest> requests = new LinkedHashMap<>();
    private final Map<String, SampleProject> projects = new LinkedHashMap<>();
    private final Map<String, SampleVersion> versions = new LinkedHashMap<>();
    private final Map<String, RndTask> tasks = new LinkedHashMap<>();
    private final Map<String, ExperimentForm> experimentForms = new LinkedHashMap<>();
    private final Map<String, TestAssignment> testAssignments = new LinkedHashMap<>();

    private int requestSequence = 1;
    private int taskSequence = 1;
    private int experimentSequence = 1;
    private int testAssignmentSequence = 1;

    public SampleWorkflowService() {
        this(Clock.systemDefaultZone());
    }

    SampleWorkflowService(Clock clock) {
        this.clock = clock;
    }

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
        return request;
    }

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
        return new ApproveSampleRequestResult(project, version, task);
    }

    public synchronized List<RndTask> taskPool() {
        return tasks.values().stream()
                .filter(task -> task.status() == RndTaskStatus.PENDING_ASSIGNMENT)
                .toList();
    }

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
        return assigned;
    }

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
        var accepted = task.accept(now());
        tasks.put(taskId, accepted);
        return accepted;
    }

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

    public synchronized List<SampleRequest> requests() {
        return new ArrayList<>(requests.values());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
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
}
