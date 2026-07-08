package com.lhr.rnd.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertColumnExists("experiment_form", "finished_yield_ratio");

        assertColumnDefinition("experiment_material", "material_category", false, "'RAW'");
        assertColumnDefinition("experiment_material", "is_primary_material", false, "FALSE");
        assertColumnDefinition("experiment_material", "input_unit", false, "'kg'");
        assertNumericColumn("experiment_material", "formula_ratio", 10, 6);
        assertNumericColumn("experiment_process", "remaining_weight_kg", 14, 4);
        assertNumericColumn("experiment_process", "loss_weight_kg", 14, 4);
        assertNumericColumn("experiment_form", "finished_output_weight_kg", 14, 4);
        assertNumericColumn("experiment_form", "finished_yield_ratio", 10, 6);
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
}
