package com.lhr.rnd.model;

public record ProcessArtifactPublic(
        String id,
        String processRevisionId,
        String artifactType,
        String documentVersion,
        String status,
        String generatedAt
) {
}
