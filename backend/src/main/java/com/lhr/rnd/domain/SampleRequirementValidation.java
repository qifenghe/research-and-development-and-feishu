package com.lhr.rnd.domain;

import com.lhr.rnd.api.BusinessException;

public final class SampleRequirementValidation {
    private SampleRequirementValidation() {}
    public static String text(String value,String label,boolean required) {
        return text(value,label,required,2000);
    }
    public static String text(String value,String label,boolean required,int maxLength) {
        var normalized=value==null?null:value.strip();
        if(normalized==null||normalized.isBlank()) {
            if(required)throw new BusinessException("SAMPLE_REQUIREMENT_REQUIRED","请填写"+label);
            return null;
        }
        if(normalized.length()>maxLength)throw new BusinessException("SAMPLE_REQUIREMENT_TOO_LONG",label+"不能超过"+maxLength+"字");
        return normalized;
    }
}
