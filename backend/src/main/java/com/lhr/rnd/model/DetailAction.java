package com.lhr.rnd.model;

public record DetailAction(
        String code,
        String label,
        String httpMethod,
        String endpoint,
        boolean primary
) {
}
