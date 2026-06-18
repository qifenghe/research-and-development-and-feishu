package com.lhr.rnd.domain;

public record SampleVersionCode(int number) {
    public SampleVersionCode {
        if (number < 0) {
            throw new IllegalArgumentException("Sample version number must be greater than or equal to 0");
        }
    }

    public static SampleVersionCode fromNumber(int number) {
        return new SampleVersionCode(number);
    }

    public String code() {
        return "A" + number;
    }
}
