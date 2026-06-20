package com.lhr.rnd.model;

import java.util.List;

public record DetailFieldGroup(
        String title,
        List<String> fields
) {
}
