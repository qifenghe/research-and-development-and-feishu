package com.lhr.rnd.domain;

import com.lhr.rnd.api.BusinessException;

public final class SampleRequirementValidation {
    private SampleRequirementValidation() {}
    public static String text(String value,String label,boolean required) {
        var normalized=value==null?null:value.strip();
        if(normalized==null||normalized.isBlank()) {
            if(required)throw new BusinessException("SAMPLE_REQUIREMENT_REQUIRED","请填写"+label);
            return null;
        }
        if(normalized.length()>2000)throw new BusinessException("SAMPLE_REQUIREMENT_TOO_LONG",label+"不能超过2000字");
        return normalized;
    }
}
