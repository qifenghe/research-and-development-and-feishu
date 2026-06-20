package com.lhr.rnd.model;

import java.util.List;

public record DashboardOverview(
        long pendingReviewCount,
        long pendingAssignmentCount,
        long pendingAcceptanceCount,
        long samplingCount,
        long pendingTestCount,
        long completedSampleCount,
        long pendingPricingCount,
        long financeNotifiedCount,
        long stoppedCount,
        List<DashboardTaskItem> recentTasks,
        List<DashboardPricingFileItem> pendingPricingFiles
) {
}
