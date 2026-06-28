package com.lhr.rnd.model;

public record SampleVersionTimelineItem(
        String versionId,
        String versionCode,
        String statusLabel,
        String subtitle,
        String summary,
        boolean locked,
        boolean current
) {
}
