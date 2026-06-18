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
        assertTableExists("archive_file");
        assertTableExists("audit_log");

        assertUniqueConstraintExists("sample_version", "uk_sample_version_project_version");
        assertUniqueConstraintExists("pricing_file", "uk_pricing_file_version_pricing_version");
        assertForeignKeyExists("rnd_task", "fk_rnd_task_version");
        assertForeignKeyExists("experiment_form", "fk_experiment_form_version");
        assertForeignKeyExists("shipment_record", "fk_shipment_record_version");
        assertForeignKeyExists("pricing_file", "fk_pricing_file_version");
        assertForeignKeyExists("finance_notification", "fk_finance_notification_pricing_file");
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
}
