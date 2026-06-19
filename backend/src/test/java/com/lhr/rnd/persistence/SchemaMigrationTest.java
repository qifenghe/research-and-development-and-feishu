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
}
