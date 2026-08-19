package com.lhr.rnd.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import com.lhr.rnd.domain.SampleStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCoreWorkflowTablesWithVersionAndPricingConstraints() {
        assertTableExists("sample_request");
        assertTableExists("sample_project");
        assertTableExists("sample_version");
        assertTableExists("rnd_task");
        assertTableExists("experiment_form");
        assertTableExists("experiment_material");
        assertTableExists("test_assignment");
        assertTableExists("test_record");
        assertTableExists("shipment_record");
        assertTableExists("customer_feedback");
        assertTableExists("pricing_file");
        assertTableExists("finance_notification");
        assertTableExists("user_account");
        assertTableExists("role_permission");
        assertTableExists("dictionary_item");
        assertTableExists("form_field_config");
        assertTableExists("workflow_rule_config");
        assertTableExists("feishu_notification");
        assertTableExists("archive_file");
        assertTableExists("audit_log");
        assertTableExists("experiment_process_plan");
        assertTableExists("experiment_major_process");
        assertTableExists("experiment_minor_step");
        assertTableExists("experiment_step_material");
        assertTableExists("experiment_process_input");
        assertTableExists("experiment_process_output");
        assertTableExists("experiment_step_output");
        assertTableExists("experiment_control_point");
        assertTableExists("experiment_control_measurement");
        assertTableExists("experiment_process_revision");
        assertTableExists("experiment_process_artifact");
        assertTableExists("pricing_packaging_item");
        assertTableExists("packaging_template_item");

        assertUniqueConstraintExists("sample_version", "uk_sample_version_project_version");
        assertUniqueConstraintExists("pricing_file", "uk_pricing_file_version_pricing_version");
        assertForeignKeyExists("rnd_task", "fk_rnd_task_version");
        assertForeignKeyExists("experiment_form", "fk_experiment_form_version");
        assertForeignKeyExists("shipment_record", "fk_shipment_record_version");
        assertForeignKeyExists("pricing_file", "fk_pricing_file_version");
        assertForeignKeyExists("finance_notification", "fk_finance_notification_pricing_file");
        assertForeignKeyExists("pricing_packaging_item", "fk_pricing_packaging_item_file");
        assertForeignKeyExists("feishu_notification", "fk_feishu_notification_user");
        assertForeignKeyExists("experiment_step_output", "fk_step_output_step");
        assertForeignKeyExists("experiment_step_material", "fk_step_material_source_output");
        assertForeignKeyExists("experiment_control_point", "fk_control_point_step");
        assertForeignKeyExists("experiment_control_measurement", "fk_control_measurement_point");
        assertForeignKeyExists("experiment_process_revision", "fk_process_revision_plan");
        assertForeignKeyExists("experiment_process_artifact", "fk_process_artifact_revision");
        assertForeignKeyExists("pricing_file", "fk_pricing_file_process_revision");

        assertColumnExists("archive_file", "category");
        assertColumnExists("archive_file", "uploaded_by");
        assertColumnExists("archive_file", "remark");
        assertColumnExists("archive_file", "content_type");
        assertColumnExists("archive_file", "file_size");
        assertColumnExists("feishu_notification", "send_attempts");
        assertColumnExists("feishu_notification", "last_error");
        assertColumnExists("role_permission", "role_code");
        assertColumnExists("role_permission", "http_method");
        assertColumnExists("role_permission", "path_pattern");
        assertColumnExists("role_permission", "enabled");
        assertColumnExists("dictionary_item", "category");
        assertColumnExists("dictionary_item", "item_code");
        assertColumnExists("dictionary_item", "item_label");
        assertColumnExists("dictionary_item", "enabled");
        assertColumnExists("form_field_config", "form_code");
        assertColumnExists("form_field_config", "field_code");
        assertColumnExists("form_field_config", "control_type");
        assertColumnExists("form_field_config", "required");
        assertColumnExists("form_field_config", "dictionary_category");
        assertColumnExists("workflow_rule_config", "workflow_code");
        assertColumnExists("workflow_rule_config", "current_status");
        assertColumnExists("workflow_rule_config", "action_code");
        assertColumnExists("workflow_rule_config", "next_status");
        assertColumnExists("workflow_rule_config", "notify_feishu");
        assertColumnExists("experiment_material", "material_category");
        assertColumnExists("experiment_material", "is_primary_material");
        assertColumnExists("experiment_material", "formula_ratio");
        assertColumnExists("experiment_material", "input_unit");
        assertColumnExists("experiment_process", "remaining_weight_kg");
        assertColumnExists("experiment_process", "remaining_disposition");
        assertColumnExists("experiment_process", "loss_weight_kg");
        assertColumnExists("experiment_form", "finished_output_weight_kg");
        assertColumnExists("experiment_form", "finished_output_quantity");
        assertColumnExists("experiment_form", "finished_output_unit");
        assertColumnExists("experiment_form", "finished_yield_percent");
        assertColumnExists("experiment_form", "yield_calculation_mode");
        assertColumnExists("rnd_task", "product_owner_name");
        assertColumnExists("pricing_file", "received_by");
        assertColumnExists("pricing_file", "received_at");
        assertColumnExists("pricing_file", "reviewed_by");
        assertColumnExists("pricing_file", "reviewed_at");
        assertColumnExists("pricing_file", "review_comment");
        assertColumnExists("pricing_file", "rejection_reason");
        assertColumnExists("pricing_file", "process_revision_id");
        assertColumnExists("experiment_step_material", "source_type");
        assertColumnExists("experiment_step_material", "source_step_output_id");
        assertColumnExists("experiment_process_plan", "balance_tolerance_kg");
        assertColumnExists("pricing_packaging_item", "source");
        assertColumnExists("pricing_packaging_item", "material_code");
        assertColumnExists("pricing_packaging_item", "quantity");
        assertColumnExists("pricing_packaging_item", "confirmation_status");
        assertColumnExists("pricing_packaging_item", "modification_reason");
        assertColumnExists("packaging_template_item", "template_code");
        assertColumnExists("packaging_template_item", "material_name");
        assertColumnExists("packaging_template_item", "conversion_type");
        assertColumnExists("packaging_template_item", "units_per_parent");
        assertColumnExists("packaging_template_item", "enabled");

        assertColumnDefinition("experiment_material", "material_category", false, "'RAW'");
        assertColumnDefinition("experiment_material", "is_primary_material", false, "FALSE");
        assertColumnDefinition("experiment_material", "input_unit", false, "'kg'");
        assertNumericColumn("experiment_material", "formula_ratio", 10, 6);
        assertNumericColumn("experiment_process", "remaining_weight_kg", 14, 4);
        assertNumericColumn("experiment_process", "loss_weight_kg", 14, 4);
        assertNumericColumn("experiment_form", "finished_output_weight_kg", 14, 4);
        assertColumnDefinition("experiment_form", "finished_output_unit", false, "U&'\\888b'");
        assertNumericColumn("experiment_form", "finished_yield_percent", 10, 6);
        assertNumericColumn("experiment_step_material", "weight_kg", 14, 4);
        assertNumericColumn("experiment_step_output", "weight_kg", 14, 4);
        assertNumericColumn("experiment_control_measurement", "measured_value", 14, 4);
        assertNumericColumn("experiment_process_input", "weight_kg", 14, 4);
        assertNumericColumn("experiment_process_output", "weight_kg", 14, 4);
        assertNumericColumn("experiment_process_plan", "balance_tolerance_kg", 14, 4);
        assertColumnDefinition("experiment_process_plan", "balance_tolerance_kg", false, "0.0100");
        assertColumnDefinition("experiment_form", "yield_calculation_mode", false, "'SELECTED_PRIMARY_MATERIALS'");
        assertColumnDefinition("experiment_step_material", "source_type", false, "'EXTERNAL'");
    }

    @Test
    void enforcesStepMaterialSourceConsistencyAndStepOutputIntegrity() {
        String prefix = "SOURCE-" + UUID.randomUUID().toString().substring(0, 8);
        var graph = insertProcessGraph(prefix);
        String downstreamStepId = prefix + "-DOWNSTREAM-STEP";
        jdbcTemplate.update("""
                insert into experiment_minor_step (id, major_process_id, sequence, step_name)
                values (?, ?, ?, ?)
                """, downstreamStepId, graph.majorId(), 2, "Downstream minor step");

        insertStepOutput(graph.outputId(), graph.stepId(), 1);
        assertThatThrownBy(() -> insertStepOutput(prefix + "-OUTPUT-DUPLICATE", graph.stepId(), 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertStepMaterial(
                prefix + "-INVALID-TYPE", graph.stepId(), 1, "UNKNOWN", null, null, null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStepMaterial(
                prefix + "-MISSING-OUTPUT", graph.stepId(), 2, "STEP_OUTPUT", null, null, null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStepMaterial(
                prefix + "-EXTERNAL-WITH-OUTPUT", graph.stepId(), 3, "EXTERNAL", graph.outputId(), null, null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStepMaterial(
                prefix + "-STEP-WITH-MASTER", downstreamStepId, 4, "STEP_OUTPUT", graph.outputId(), "RAW-001", null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStepMaterial(
                prefix + "-STEP-WITH-FORMULA", downstreamStepId, 5, "STEP_OUTPUT", graph.outputId(), null, "FORMULA-001"
        )).isInstanceOf(DataIntegrityViolationException.class);

        insertStepMaterial(
                prefix + "-VALID-STEP", downstreamStepId, 6, "STEP_OUTPUT", graph.outputId(), null, null
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from experiment_step_output where id = ?", graph.outputId()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from experiment_step_material where id = ?", Integer.class, prefix + "-VALID-STEP"
        )).isEqualTo(1);

        jdbcTemplate.update("delete from experiment_minor_step where id = ?", downstreamStepId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from experiment_step_material where id = ?", Integer.class, prefix + "-VALID-STEP"
        )).isZero();
        jdbcTemplate.update("delete from experiment_minor_step where id = ?", graph.stepId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from experiment_step_output where id = ?", Integer.class, graph.outputId()
        )).isZero();
    }

    @Test
    void allowsPricingFilesWithoutProcessRevision() {
        String prefix = "PRICING-" + UUID.randomUUID().toString().substring(0, 8);
        var form = insertExperimentForm(prefix);

        jdbcTemplate.update("""
                insert into pricing_file (
                    id, version_id, sample_no, product_name, version_code, pricing_version,
                    file_name, status, content_length, generated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """,
                prefix + "-FILE", form.versionId(), prefix + "-SAMPLE", "Test product", "A0", "V1",
                "pricing.xlsx", "DRAFT", 0
        );

        assertThat(jdbcTemplate.queryForObject(
                "select process_revision_id from pricing_file where id = ?", String.class, prefix + "-FILE"
        )).isNull();
    }

    @Test
    void backfillsMaterialCategoryFromLegacyStageValues() {
        String databaseUrl = "jdbc:h2:mem:material-backfill-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        var legacyFlyway = Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("7")
                .load();
        legacyFlyway.migrate();

        var legacyJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", ""));
        insertLegacyExperimentMaterials(legacyJdbcTemplate);

        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("9")
                .load()
                .migrate();

        assertThat(legacyJdbcTemplate.queryForList(
                "select material_category from experiment_material order by sequence",
                String.class
        )).containsExactly("RAW", "AUXILIARY", "PACKAGING", "RAW");
    }

    @Test
    void v11BackfillsProductOwnerAndEnforcesFinishedOutputConstraints() {
        String databaseUrl = "jdbc:h2:mem:product-owner-backfill-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        var legacyFlyway = Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("10")
                .load();
        legacyFlyway.migrate();

        var legacyJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", ""));
        insertLegacyAssignedTask(legacyJdbcTemplate);

        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("11")
                .load()
                .migrate();

        assertThat(legacyJdbcTemplate.queryForObject(
                "select product_owner_name from rnd_task where id = 'TASK-OWNER-LEGACY'",
                String.class
        )).isEqualTo("Legacy assignee");
        assertThat(legacyJdbcTemplate.update(
                "update experiment_form set finished_output_quantity = null, finished_output_unit = '盒' where id = 'FORM-OWNER-LEGACY'"
        )).isEqualTo(1);
        assertThatThrownBy(() -> legacyJdbcTemplate.update(
                "update experiment_form set finished_output_quantity = 0 where id = 'FORM-OWNER-LEGACY'"
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> legacyJdbcTemplate.update(
                "update experiment_form set finished_output_unit = '桶' where id = 'FORM-OWNER-LEGACY'"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v12ConvertsLegacyFinishedYieldRatiosToPercents() {
        String databaseUrl = "jdbc:h2:mem:finished-yield-percent-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("11")
                .load()
                .migrate();

        var legacyJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", ""));
        insertLegacyAssignedTask(legacyJdbcTemplate);
        legacyJdbcTemplate.update(
                "update experiment_form set finished_yield_ratio = 1.2 where id = 'FORM-OWNER-LEGACY'"
        );

        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("12")
                .load()
                .migrate();

        assertThat(legacyJdbcTemplate.queryForObject(
                "select finished_yield_percent from experiment_form where id = 'FORM-OWNER-LEGACY'",
                java.math.BigDecimal.class
        )).isEqualByComparingTo("120");
    }

    @Test
    void v13MigratesLegacyPricingWorkflowRulesToReviewablePricingStates() {
        String databaseUrl = "jdbc:h2:mem:pricing-review-workflow-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("12")
                .load()
                .migrate();

        var legacyJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", ""));
        insertLegacyPricingWorkflowRules(legacyJdbcTemplate);

        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("13")
                .load()
                .migrate();

        var generatedPricingTarget = legacyJdbcTemplate.queryForObject(
                "select next_status from workflow_rule_config where workflow_code = ? and current_status = ? and action_code = ?",
                String.class,
                "SAMPLE_RND_FLOW",
                "SAMPLE_COMPLETED",
                "REQUEST_PRICING"
        );
        assertThat(generatedPricingTarget).isEqualTo("PENDING_PRICING_REVIEW");
        assertThat(SampleStatus.valueOf(generatedPricingTarget)).isEqualTo(SampleStatus.PENDING_PRICING_REVIEW);
        assertThat(legacyJdbcTemplate.queryForObject(
                "select current_status from workflow_rule_config where workflow_code = ? and action_code = ?",
                String.class,
                "SAMPLE_RND_FLOW",
                "NOTIFY_FINANCE"
        )).isEqualTo("PRICING_APPROVED");
        assertThat(legacyJdbcTemplate.queryForObject(
                "select count(*) from workflow_rule_config where workflow_code = ? and current_status = ? and action_code in (?, ?)",
                Integer.class,
                "SAMPLE_RND_FLOW",
                "PENDING_PRICING_REVIEW",
                "APPROVE_PRICING",
                "REJECT_PRICING"
        )).isEqualTo(2);
    }

    @Test
    void v13AddsPricingReadAndDownloadPermissionsToExistingRndEngineerRole() {
        String databaseUrl = "jdbc:h2:mem:pricing-owner-permission-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("12")
                .load()
                .migrate();

        var legacyJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", ""));
        legacyJdbcTemplate.update(
                """
                        insert into role_permission (
                            id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)
                        """,
                "PERM-LEGACY-RND-TASK-READ",
                "RND_ENGINEER",
                "GET",
                "/api/v1/rnd-tasks",
                true,
                "旧版研发任务读取权限",
                30
        );

        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("13")
                .load()
                .migrate();

        assertThat(legacyJdbcTemplate.queryForList(
                "select path_pattern from role_permission where role_code = ? and http_method = ? order by path_pattern",
                String.class,
                "RND_ENGINEER",
                "GET"
        )).contains(
                "/api/v1/pricing-files",
                "/api/v1/pricing-files/*/detail",
                "/api/v1/pricing-files/*/download"
        );
    }

    @Test
    void v23GrantsSubmissionCheckReadPermissionToExistingProcessPlanReadersWithoutAddingWritePermission() {
        String databaseUrl = "jdbc:h2:mem:process-submission-check-permission-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("22")
                .load()
                .migrate();

        var legacyJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", ""));
        for (var role : java.util.List.of("RND_DIRECTOR", "RND_ENGINEER", "TESTER")) {
            legacyJdbcTemplate.update(
                    """
                            insert into role_permission (
                                id, role_code, http_method, path_pattern, enabled, description, sort_order, updated_at
                            ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)
                            """,
                    "PERM-LEGACY-" + role,
                    role,
                    "GET",
                    "/api/v1/experiment-forms/*/process-plan",
                    true,
                    "旧版分层工艺读取权限",
                    10
            );
        }

        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("23")
                .load()
                .migrate();

        for (var role : java.util.List.of("RND_DIRECTOR", "RND_ENGINEER", "TESTER")) {
            assertThat(legacyJdbcTemplate.queryForObject(
                    "select count(*) from role_permission where role_code = ? and http_method = ? and path_pattern = ?",
                    Integer.class,
                    role,
                    "GET",
                    "/api/v1/experiment-forms/*/process-plan/submission-check"
            )).isEqualTo(1);
        }
        assertThat(legacyJdbcTemplate.queryForObject(
                "select count(*) from role_permission where http_method = ? and path_pattern = ?",
                Integer.class,
                "PUT",
                "/api/v1/experiment-forms/*/process-plan/submission-check"
        )).isZero();
        assertThat(legacyJdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" is not null and \"success\" = true",
                Integer.class
        )).isEqualTo(23);
    }

    private void insertLegacyPricingWorkflowRules(JdbcTemplate legacyJdbcTemplate) {
        legacyJdbcTemplate.update(
                """
                        insert into workflow_rule_config (
                            id, workflow_code, current_status, action_code, action_label, next_status,
                            enabled, notify_feishu, notify_role, sort_order, remark, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                        """,
                "FLOW-LEGACY-REQUEST-PRICING",
                "SAMPLE_RND_FLOW",
                "SAMPLE_COMPLETED",
                "REQUEST_PRICING",
                "生成核价",
                "PRICING_FILE_GENERATED",
                true,
                true,
                "FINANCE",
                110,
                "旧版生成核价"
        );
        legacyJdbcTemplate.update(
                """
                        insert into workflow_rule_config (
                            id, workflow_code, current_status, action_code, action_label, next_status,
                            enabled, notify_feishu, notify_role, sort_order, remark, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                        """,
                "FLOW-LEGACY-NOTIFY-FINANCE",
                "SAMPLE_RND_FLOW",
                "PRICING_FILE_GENERATED",
                "NOTIFY_FINANCE",
                "通知财务",
                "FINANCE_NOTIFIED",
                true,
                true,
                "FINANCE",
                120,
                "旧版通知财务"
        );
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'PUBLIC'
                          and table_name = ?
                        """,
                Integer.class,
                tableName.toUpperCase()
        );
        assertThat(count).as("table %s exists", tableName).isEqualTo(1);
    }

    private void assertUniqueConstraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.table_constraints
                        where table_schema = 'PUBLIC'
                          and table_name = ?
                          and constraint_name = ?
                          and constraint_type = 'UNIQUE'
                        """,
                Integer.class,
                tableName.toUpperCase(),
                constraintName.toUpperCase()
        );
        assertThat(count).as("unique constraint %s exists", constraintName).isEqualTo(1);
    }

    private void assertForeignKeyExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.table_constraints
                        where table_schema = 'PUBLIC'
                          and table_name = ?
                          and constraint_name = ?
                          and constraint_type = 'FOREIGN KEY'
                        """,
                Integer.class,
                tableName.toUpperCase(),
                constraintName.toUpperCase()
        );
        assertThat(count).as("foreign key %s exists", constraintName).isEqualTo(1);
    }

    private void assertColumnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_schema = 'PUBLIC'
                          and table_name = ?
                          and column_name = ?
                        """,
                Integer.class,
                tableName.toUpperCase(),
                columnName.toUpperCase()
        );
        assertThat(count).as("column %s.%s exists", tableName, columnName).isEqualTo(1);
    }

    private void assertColumnDefinition(
            String tableName,
            String columnName,
            boolean nullable,
            String defaultExpression
    ) {
        var metadata = jdbcTemplate.queryForMap(
                """
                        select is_nullable, column_default
                        from information_schema.columns
                        where table_schema = 'PUBLIC'
                          and table_name = ?
                          and column_name = ?
                        """,
                tableName.toUpperCase(),
                columnName.toUpperCase()
        );
        assertThat(metadata.get("IS_NULLABLE"))
                .as("column %s.%s nullability", tableName, columnName)
                .isEqualTo(nullable ? "YES" : "NO");
        assertThat(metadata.get("COLUMN_DEFAULT"))
                .as("column %s.%s default", tableName, columnName)
                .isEqualTo(defaultExpression);
    }

    private void assertNumericColumn(String tableName, String columnName, int precision, int scale) {
        var metadata = jdbcTemplate.queryForMap(
                """
                        select numeric_precision, numeric_scale
                        from information_schema.columns
                        where table_schema = 'PUBLIC'
                          and table_name = ?
                          and column_name = ?
                        """,
                tableName.toUpperCase(),
                columnName.toUpperCase()
        );
        assertThat(((Number) metadata.get("NUMERIC_PRECISION")).intValue())
                .as("column %s.%s precision", tableName, columnName)
                .isEqualTo(precision);
        assertThat(((Number) metadata.get("NUMERIC_SCALE")).intValue())
                .as("column %s.%s scale", tableName, columnName)
                .isEqualTo(scale);
    }

    private ProcessGraph insertProcessGraph(String prefix) {
        var form = insertExperimentForm(prefix);
        String planId = prefix + "-PLAN";
        String majorId = prefix + "-MAJOR";
        String stepId = prefix + "-STEP";
        String outputId = prefix + "-OUTPUT";
        jdbcTemplate.update("""
                insert into experiment_process_plan (id, experiment_form_id, created_at, updated_at)
                values (?, ?, current_timestamp, current_timestamp)
                """, planId, form.formId());
        jdbcTemplate.update("""
                insert into experiment_major_process (id, process_plan_id, sequence, process_name)
                values (?, ?, ?, ?)
                """, majorId, planId, 1, "Test major process");
        jdbcTemplate.update("""
                insert into experiment_minor_step (id, major_process_id, sequence, step_name)
                values (?, ?, ?, ?)
                """, stepId, majorId, 1, "Test minor step");
        return new ProcessGraph(majorId, stepId, outputId);
    }

    private TestForm insertExperimentForm(String prefix) {
        String projectId = prefix + "-PROJECT";
        String versionId = prefix + "-VERSION";
        String taskId = prefix + "-TASK";
        String formId = prefix + "-FORM";
        String sampleNo = prefix + "-SAMPLE";
        jdbcTemplate.update("""
                insert into sample_project (
                    id, sample_no, product_name, product_type, customer_name, specification, status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """, projectId, sampleNo, "Test product", "TEST", "Test customer", "Test spec", "SAMPLING");
        jdbcTemplate.update("""
                insert into sample_version (
                    id, project_id, sample_no, product_name, product_type, specification, version_no,
                    version_number, version_code, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """, versionId, projectId, sampleNo, "Test product", "TEST", "Test spec", "A0", 1, "A0");
        jdbcTemplate.update("""
                insert into rnd_task (
                    id, project_id, version_id, sample_no, product_name, version_code, status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """, taskId, projectId, versionId, sampleNo, "Test product", "A0", "SAMPLING");
        jdbcTemplate.update("""
                insert into experiment_form (
                    id, task_id, project_id, version_id, sample_no, product_name, version_code, status,
                    operator_name, saved_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                """, formId, taskId, projectId, versionId, sampleNo, "Test product", "A0", "DRAFT", "Test operator");
        return new TestForm(formId, versionId);
    }

    private void insertStepOutput(String outputId, String stepId, int sequence) {
        jdbcTemplate.update("""
                insert into experiment_step_output (
                    id, minor_step_id, sequence, output_type, output_name
                ) values (?, ?, ?, ?, ?)
                """, outputId, stepId, sequence, "INTERMEDIATE", "Intermediate output");
    }

    private void insertStepMaterial(
            String materialId,
            String stepId,
            int sequence,
            String sourceType,
            String sourceStepOutputId,
            String materialCode,
            String formulaMaterialId
    ) {
        jdbcTemplate.update("""
                insert into experiment_step_material (
                    id, minor_step_id, sequence, material_role, material_name, source_type,
                    source_step_output_id, material_code, formula_material_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, materialId, stepId, sequence, "PRIMARY", "Test material", sourceType,
                sourceStepOutputId, materialCode, formulaMaterialId);
    }

    private record TestForm(String formId, String versionId) {
    }

    private record ProcessGraph(String majorId, String stepId, String outputId) {
    }

    private void insertLegacyExperimentMaterials(JdbcTemplate template) {
        template.update("""
                insert into sample_project (
                    id, sample_no, product_name, product_type, customer_name, specification, status, created_at
                ) values ('PROJECT-LEGACY', 'SAMPLE-LEGACY', 'Legacy product', 'TEST', 'Legacy customer',
                          'Legacy spec', 'SAMPLING', current_timestamp)
                """);
        template.update("""
                insert into sample_version (
                    id, project_id, sample_no, product_name, product_type, specification, version_no,
                    version_number, version_code, created_at
                ) values ('VERSION-LEGACY', 'PROJECT-LEGACY', 'SAMPLE-LEGACY', 'Legacy product', 'TEST',
                          'Legacy spec', 'A0', 1, 'A0', current_timestamp)
                """);
        template.update("""
                insert into rnd_task (
                    id, project_id, version_id, sample_no, product_name, version_code, status, created_at
                ) values ('TASK-LEGACY', 'PROJECT-LEGACY', 'VERSION-LEGACY', 'SAMPLE-LEGACY',
                          'Legacy product', 'A0', 'SAMPLING', current_timestamp)
                """);
        template.update("""
                insert into experiment_form (
                    id, task_id, project_id, version_id, sample_no, product_name, version_code, status,
                    operator_name, saved_at
                ) values ('FORM-LEGACY', 'TASK-LEGACY', 'PROJECT-LEGACY', 'VERSION-LEGACY', 'SAMPLE-LEGACY',
                          'Legacy product', 'A0', 'DRAFT', 'Legacy operator', current_timestamp)
                """);
        template.update("""
                insert into experiment_material (
                    id, experiment_form_id, stage, sequence, material_name, weight_kg, utilization_rate
                ) values
                    ('MATERIAL-RAW', 'FORM-LEGACY', '原料', 1, 'Raw material', 10, 1),
                    ('MATERIAL-AUX', 'FORM-LEGACY', '辅料', 2, 'Auxiliary material', 2, 1),
                    ('MATERIAL-PACKAGING', 'FORM-LEGACY', '包材', 3, 'Packaging material', 1, 1),
                    ('MATERIAL-DEFAULT', 'FORM-LEGACY', null, 4, 'Default material', 1, 1)
                """);
    }

    private void insertLegacyAssignedTask(JdbcTemplate template) {
        template.update("""
                insert into sample_project (
                    id, sample_no, product_name, product_type, customer_name, specification, status, created_at
                ) values ('PROJECT-OWNER-LEGACY', 'SAMPLE-OWNER-LEGACY', 'Legacy product', 'TEST',
                          'Legacy customer', 'Legacy spec', 'SAMPLING', current_timestamp)
                """);
        template.update("""
                insert into sample_version (
                    id, project_id, sample_no, product_name, product_type, specification, version_no,
                    version_number, version_code, created_at
                ) values ('VERSION-OWNER-LEGACY', 'PROJECT-OWNER-LEGACY', 'SAMPLE-OWNER-LEGACY',
                          'Legacy product', 'TEST', 'Legacy spec', 'A0', 1, 'A0', current_timestamp)
                """);
        template.update("""
                insert into rnd_task (
                    id, project_id, version_id, sample_no, product_name, version_code, status, assignee_name, created_at
                ) values ('TASK-OWNER-LEGACY', 'PROJECT-OWNER-LEGACY', 'VERSION-OWNER-LEGACY',
                          'SAMPLE-OWNER-LEGACY', 'Legacy product', 'A0', 'SAMPLING', 'Legacy assignee', current_timestamp)
                """);
        template.update("""
                insert into experiment_form (
                    id, task_id, project_id, version_id, sample_no, product_name, version_code, status,
                    operator_name, saved_at
                ) values ('FORM-OWNER-LEGACY', 'TASK-OWNER-LEGACY', 'PROJECT-OWNER-LEGACY',
                          'VERSION-OWNER-LEGACY', 'SAMPLE-OWNER-LEGACY', 'Legacy product', 'A0', 'DRAFT',
                          'Legacy operator', current_timestamp)
                """);
    }
}
