package com.lhr.rnd.service;

public record FeishuSendResult(boolean success, String errorMessage) {
    public static FeishuSendResult sent() {
        return new FeishuSendResult(true, null);
    }

    public static FeishuSendResult failed(String errorMessage) {
        return new FeishuSendResult(false, errorMessage);
    }
}
