package com.lhr.rnd.domain;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class FinishedOutputValidatorTest {
    @Test void kilogramsKeepDecimalsAndMissingValues() {
        assertThat(FinishedOutputValidator.validate(new BigDecimal("12.345"),new BigDecimal("12.345"),"kg")).isEqualByComparingTo("12.345");
        assertThat(FinishedOutputValidator.validate(null,null,"kg")).isNull();
        assertThatThrownBy(()->FinishedOutputValidator.validate(new BigDecimal("12"),new BigDecimal("10"),"kg")).hasMessageContaining("一致");
        assertThatThrownBy(()->FinishedOutputValidator.validate(null,BigDecimal.ONE,"kg")).hasMessageContaining("一致");
    }
    @Test void bagsRemainPositiveIntegers() {
        assertThat(FinishedOutputValidator.validate(new BigDecimal("89"),new BigDecimal("178"),"袋")).isEqualByComparingTo("178");
        assertThatThrownBy(()->FinishedOutputValidator.validate(BigDecimal.ONE,new BigDecimal("1.5"),"袋")).hasMessageContaining("正整数");
    }
}
