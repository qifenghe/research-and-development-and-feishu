package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class ProcessRecipeServiceTest {
    private final ProcessRecipeService service = new ProcessRecipeService();

    @Test
    void aggregatesOnlyExternalMaterialsByFormulaIdAndKeepsStepSources() {
        var lines = service.aggregate(plan(
                step(1, material(1, "PRIMARY", "BEEF", "  鲜牛腩  ", "10", "BEEF-1", "EXTERNAL", null),
                        output("CUT-OUT", "修割牛腩", "10", true, true)),
                step(2, material(1, "PRIMARY", null, "修割牛腩", "10", null, "STEP_OUTPUT", "CUT-OUT"),
                        material(2, "AUXILIARY", "SALT", "食盐", "0.080", "SALT-1", "EXTERNAL", null),
                        output("BOIL-OUT", "焯水牛腩", "9", true, true)),
                step(3, material(1, "PRIMARY", null, "焯水牛腩", "9", null, "STEP_OUTPUT", "BOIL-OUT"),
                        material(2, "AUXILIARY", "SALT", " 食盐 ", "0.100", "SALT-1", "EXTERNAL", null),
                        output("FINISHED-OUT", "熟制牛腩", "8", true, false))));

        assertThat(lines).extracting(ProcessRecipeService.RecipeLine::materialName,
                        ProcessRecipeService.RecipeLine::weightKg,
                        ProcessRecipeService.RecipeLine::ratioPercent)
                .containsExactly(tuple("鲜牛腩", bd("10.0000"), bd("98.231827")),
                        tuple("食盐", bd("0.1800"), bd("1.768173")));
        assertThat(lines.get(1).sources()).extracting(ProcessRecipeService.RecipeSource::stepSequence)
                .containsExactly(2, 3);
    }

    @Test
    void fallsBackToMaterialCodeThenNormalizedNameForGroupingAndOrdering() {
        var lines = service.aggregate(plan(
                step(1, material(1, "AUXILIARY", "SPICE", " 香辛料 ", "1", null, "EXTERNAL", null), null),
                step(2, material(1, "AUXILIARY", "SPICE", "香辛料", "2", null, "EXTERNAL", null), null),
                step(3, material(1, "AUXILIARY", null, "  水  ", "3", null, "EXTERNAL", null), null)));

        assertThat(lines).extracting(ProcessRecipeService.RecipeLine::materialCode,
                        ProcessRecipeService.RecipeLine::materialName,
                        ProcessRecipeService.RecipeLine::weightKg)
                .containsExactly(tuple("SPICE", "香辛料", bd("3.0000")), tuple(null, "水", bd("3.0000")));
    }

    @Test
    void usesPrimaryAsCanonicalRoleWhenOneMaterialHasMixedRolesAcrossSteps() {
        var lines = service.aggregate(plan(
                step(1, material(1, "AUXILIARY", "BEEF", "牛肉", "0.2", "BEEF-1", "EXTERNAL", null), null),
                step(2, material(1, "PRIMARY", "BEEF", "牛肉", "10", "BEEF-1", "EXTERNAL", null),
                        output("BEEF-OUT", "牛肉产出", "9", true, false))));

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.canonicalMaterialRole()).isEqualTo("PRIMARY");
            assertThat(line.weightKg()).isEqualByComparingTo("10.2000");
        });
    }

    private ProcessPlan plan(ProcessPlan.MinorStep... steps) {
        var major = new ProcessPlan.MajorProcess("MAJOR-1", 1, "HEAT", "热加工", null, "PRIMARY_INPUT", null,
                List.of(steps), List.of(), List.of(), null);
        return new ProcessPlan("PLAN-1", "FORM-1", 1, "DRAFT", List.of(major), null, false);
    }

    private ProcessPlan.MinorStep step(int sequence, ProcessPlan.StepMaterial first, ProcessPlan.StepOutput output) {
        return step(sequence, first, null, output);
    }

    private ProcessPlan.MinorStep step(int sequence, ProcessPlan.StepMaterial first, ProcessPlan.StepMaterial second,
                                       ProcessPlan.StepOutput output) {
        var materials = second == null ? List.of(first) : List.of(first, second);
        return new ProcessPlan.MinorStep("STEP-" + sequence, sequence, null, "步骤" + sequence, "NORMAL", null, null,
                null, null, null, null, null, null, materials, output == null ? List.of() : List.of(output), List.of());
    }

    private ProcessPlan.StepMaterial material(int sequence, String role, String code, String name, String weight,
                                              String formulaId, String sourceType, String sourceOutputId) {
        return new ProcessPlan.StepMaterial("MAT-" + sequence + '-' + name, sequence, role, code, name, "SOLID",
                new BigDecimal(weight), formulaId, null, sourceType, sourceOutputId);
    }

    private ProcessPlan.StepOutput output(String id, String name, String weight, boolean primary, boolean continueFlow) {
        return new ProcessPlan.StepOutput(id, 1, "INTERMEDIATE", name, "SOLID", new BigDecimal(weight), primary,
                continueFlow, null);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
