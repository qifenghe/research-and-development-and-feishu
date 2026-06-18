package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssignRndTaskRequest(@NotBlank String assigneeName, @NotNull LocalDate dueDate) {
}
