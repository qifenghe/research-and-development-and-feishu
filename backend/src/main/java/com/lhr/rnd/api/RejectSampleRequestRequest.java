package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RejectSampleRequestRequest(@NotNull(message="请刷新需求") @PositiveOrZero Integer versionNo,
                                        @NotBlank(message="请填写驳回批语") @Size(max=2000,message="驳回批语不能超过2000字") String comment) {}
