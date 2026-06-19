package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.WorkflowRuleConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRuleConfigRepository extends JpaRepository<WorkflowRuleConfigEntity, String> {
    long countByWorkflowCode(String workflowCode);

    List<WorkflowRuleConfigEntity> findByWorkflowCodeOrderBySortOrderAsc(String workflowCode);

    List<WorkflowRuleConfigEntity> findAllByOrderByWorkflowCodeAscSortOrderAsc();

    Optional<WorkflowRuleConfigEntity> findByWorkflowCodeAndCurrentStatusAndActionCode(
            String workflowCode,
            String currentStatus,
            String actionCode
    );

    void deleteByWorkflowCode(String workflowCode);
}
