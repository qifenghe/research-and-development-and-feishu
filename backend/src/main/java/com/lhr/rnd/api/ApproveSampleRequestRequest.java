package com.lhr.rnd.api;

import jakarta.validation.constraints.PositiveOrZero;

public record ApproveSampleRequestRequest(String reviewerName, @PositiveOrZero Integer versionNo) {
}
