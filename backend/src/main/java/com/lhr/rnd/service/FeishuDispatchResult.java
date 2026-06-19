package com.lhr.rnd.service;

public record FeishuDispatchResult(
        int attemptedCount,
        int sentCount,
        int failedCount
) {
}
