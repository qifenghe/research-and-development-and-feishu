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

        assertUniqueConstraintExists("sample_version", "uk_sample_version_project_version");
        assertUniqueConstraintExists("pricing_file", "uk_pricing_file_version_pricing_version");
        assertForeignKeyExists("rnd_task", "fk_rnd_task_version");
        assertForeignKeyExists("experiment_form", "fk_experiment_form_version");
        assertForeignKeyExists("shipment_record", "fk_shipment_record_version");
        assertForeignKeyExists("pricing_file", "fk_pricing_file_version");
        assertForeignKeyExists("finance_notification", "fk_finance_notification_pricing_file");
        assertForeignKeyExists("feishu_notification", "fk_feishu_notification_user");

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

        assertColumnDefinition("experiment_material", "material_category", false, "'RAW'");
        assertColumnDefinition("experiment_material", "is_primary_material", false, "FALSE");
        assertColumnDefinition("experiment_material", "input_unit", false, "'kg'");
        assertNumericColumn("experiment_material", "formula_ratio", 10, 6);
        assertNumericColumn("experiment_process", "remaining_weight_kg", 14, 4);
        assertNumericColumn("experiment_process", "loss_weight_kg", 14, 4);
        assertNumericColumn("experiment_form", "finished_output_weight_kg", 14, 4);
        assertColumnDefinition("experiment_form", "finished_output_unit", false, "U&'\\888b'");
        assertNumericColumn("experiment_form", "finished_yield_percent", 10, 6);
        assertColumnDefinition("experiment_form", "yield_calculation_mode", false, "'SELECTED_PRIMARY_MATERIALS'");
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
