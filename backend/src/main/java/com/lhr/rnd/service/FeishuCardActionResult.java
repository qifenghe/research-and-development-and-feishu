package com.lhr.rnd.service;

import com.lhr.rnd.model.RndTask;

public record FeishuCardActionResult(
        String action,
        String message,
        RndTask task
) {
}
