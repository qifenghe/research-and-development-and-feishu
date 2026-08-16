package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessPlanCalculationServiceTest {
    private final ProcessPlanCalculationService service = new ProcessPlanCalculationService();

    @Test
    void calculatesMainYieldRecoveryAndBalanceDifference() {
        var process = major(
                List.of(input("PRIMARY", "10"), input("AUXILIARY", "2")),
                List.of(output("QUALIFIED", "8"), output("REUSABLE", "1"), output("WASTE", "3")));

        var result = service.calculate(process);

        assertThat(result.primaryInputWeightKg()).isEqualByComparingTo("10.0000");
        assertThat(result.totalInputWeightKg()).isEqualByComparingTo("12.0000");
        assertThat(result.qualifiedOutputWeightKg()).isEqualByComparingTo("8.0000");
        assertThat(result.mainYieldPercent()).isEqualByComparingTo("80.000000");
        assertThat(result.recoveryPercent()).isEqualByComparingTo("75.000000");
        assertThat(result.balanceDifferenceKg()).isEqualByComparingTo("0.0000");
    }

    @Test
    void returnsNullRateWhenBasisIsZero() {
        var result = service.calculate(major(List.of(), List.of()));

        assertThat(result.mainYieldPercent()).isNull();
        assertThat(result.recoveryPercent()).isNull();
    }

    @Test
    void calculatesBatchYieldFromFirstPrimaryInputAndLastQualifiedOutput() {
        var first = major(List.of(input("PRIMARY", "10")), List.of(output("QUALIFIED", "9")));
        var last = major(List.of(input("PRIMARY", "9")), List.of(output("QUALIFIED", "7.5")));
        var plan = new ProcessPlan(null, "FORM-1", 1, "DRAFT", List.of(first, last), null, false);

        assertThat(service.calculateBatch(plan)).isEqualByComparingTo("75.000000");
    }

    private ProcessPlan.MajorProcess major(
            List<ProcessPlan.ProcessInput> inputs,
            List<ProcessPlan.ProcessOutput> outputs
    ) {
        return new ProcessPlan.MajorProcess(null, 1, "HEAT", "热加工", null,
                "PRIMARY_INPUT", null, List.of(), inputs, outputs, null);
    }

    private ProcessPlan.ProcessInput input(String role, String weight) {
        return new ProcessPlan.ProcessInput(null, 1, role, null, role, new BigDecimal(weight), null);
    }

    private ProcessPlan.ProcessOutput output(String type, String weight) {
        return new ProcessPlan.ProcessOutput(null, 1, type, new BigDecimal(weight), null);
    }
}
