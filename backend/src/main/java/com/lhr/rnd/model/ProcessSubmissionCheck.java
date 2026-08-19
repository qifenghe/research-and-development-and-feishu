package com.lhr.rnd.model;

import java.util.List;

public record ProcessSubmissionCheck(
        boolean ready,
        List<Issue> errors,
        List<Issue> warnings
) {
    public ProcessSubmissionCheck {
        errors = List.copyOf(errors == null ? List.of() : errors);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        ready = errors.isEmpty();
    }

    public record Issue(
            String code,
            String message,
            Integer majorSequence,
            Integer stepSequence
    ) {
    }
}
