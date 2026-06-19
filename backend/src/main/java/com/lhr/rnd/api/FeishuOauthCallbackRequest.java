package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record FeishuOauthCallbackRequest(@NotBlank String code) {
}
