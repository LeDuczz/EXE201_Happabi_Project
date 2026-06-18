package com.minduc.happabi.config;

import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseEnumConstraintSynchronizer {

    private final JdbcTemplate jdbcTemplate;

    public void syncCodeFirstEnumChecks() {
        syncEnumCheck(
                "wallet_transaction",
                "transaction_type",
                "wallet_transaction_transaction_type_check",
                Arrays.stream(TransactionType.values()).map(Enum::name).toArray(String[]::new)
        );
        syncEnumCheck(
                "notifications",
                "type",
                "notifications_type_check",
                Arrays.stream(NotificationType.values()).map(Enum::name).toArray(String[]::new)
        );
    }

    private void syncEnumCheck(String tableName, String columnName, String constraintName, String[] allowedValues) {
        if (!tableExists(tableName)) {
            log.debug("[Schema] Skip enum check sync because table does not exist: {}", tableName);
            return;
        }

        jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT IF EXISTS " + constraintName);
        jdbcTemplate.execute("ALTER TABLE " + tableName
                + " ADD CONSTRAINT " + constraintName
                + " CHECK (" + columnName + " IN (" + toSqlStringList(allowedValues) + "))");
        log.info("[Schema] Synced enum check constraint {} on {}.{}", constraintName, tableName, columnName);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private String toSqlStringList(String[] values) {
        return Arrays.stream(values)
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
    }
}
