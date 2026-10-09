package com.lhr.rnd.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResubmitSampleRequestRequest(@NotNull @PositiveOrZero Integer versionNo,
                                          @Valid @NotNull CreateSampleRequestRequest request) {}
