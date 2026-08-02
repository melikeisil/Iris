package com.smartwork.integration;

import com.smartwork.support.IntegrationTest;
import com.smartwork.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-013. "Excluded from the formula" is a policy that erodes; "the column does not
 * exist" is a property.
 *
 * <p>This test fails the build if anyone ever adds a protected-characteristic column,
 * whatever their intent. It is the enforcement mechanism behind the claim that assignment
 * scoring cannot use them - not a comment, not a code review convention.
 */
@IntegrationTest
@Import(PostgresContainerConfig.class)
@DisplayName("No protected personal characteristics exist in the schema")
class ProtectedCharacteristicsSchemaIT {

    private static final Set<String> FORBIDDEN_COLUMN_FRAGMENTS = Set.of(
            "gender", "sex", "age", "birth_date", "date_of_birth", "dob",
            "race", "ethnic", "religio", "disab", "political", "party",
            "marital", "family_status", "nationality", "citizenship",
            "pregnan", "sexual_orientation", "union_member");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void noBusinessTableCarriesAProtectedCharacteristic() {
        List<String> offending = jdbc.query("""
                SELECT table_name || '.' || column_name AS qualified
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name NOT LIKE 'flyway%'
                """, (rs, row) -> rs.getString("qualified"))
                .stream()
                .filter(ProtectedCharacteristicsSchemaIT::looksProtected)
                .toList();

        assertThat(offending)
                .as("Protected personal characteristics must not exist in the schema (ADR-013). "
                        + "If a business need appears genuine, it must be argued explicitly, not "
                        + "introduced by adding a column.")
                .isEmpty();
    }

    private static boolean looksProtected(String qualifiedColumn) {
        String column = qualifiedColumn.substring(qualifiedColumn.indexOf('.') + 1).toLowerCase();
        return FORBIDDEN_COLUMN_FRAGMENTS.stream().anyMatch(fragment ->
                // Substring matching would flag "message" for "age"; match on token boundaries.
                column.equals(fragment)
                        || column.startsWith(fragment + "_")
                        || column.endsWith("_" + fragment)
                        || column.contains("_" + fragment + "_"));
    }
}
