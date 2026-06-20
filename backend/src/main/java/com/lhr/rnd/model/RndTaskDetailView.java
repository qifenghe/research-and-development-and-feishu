package com.lhr.rnd.model;

import java.util.List;

public record RndTaskDetailView(
        RndTask task,
        SampleVersion version,
        ExperimentForm currentExperimentForm,
        TestAssignment currentTestAssignment,
        List<DetailFieldGroup> fieldGroups,
        List<DetailAction> availableActions
) {
}
