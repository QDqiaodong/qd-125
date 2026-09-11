package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 移交单号按日期持久化发号器表迁移。
 * 新表替代进程内计数器，应用重启或多实例并发时仍由数据库行锁保证发号唯一。
 */
@Component
public class TransferSequenceSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(TransferSequenceSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public TransferSequenceSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        try {
            log.info("[V3迁移] 检查/创建移交单号发号器表 block_transfer_sequence");
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS block_transfer_sequence (" +
                    "    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY," +
                    "    current_value INT NOT NULL" +
                    ")");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V3迁移] 发号器表已存在，跳过");
        }
        ensureInitializationLock();
    }

    private void ensureInitializationLock() {
        try {
            jdbcTemplate.update(
                    "INSERT INTO block_transfer_sequence (sequence_date, current_value) VALUES ('LOCK', 0)");
            log.info("[V3迁移] 已初始化发号器并发建号锁");
        } catch (Exception e) {
            // 已存在锁行即可；只忽略主键/唯一键冲突，其他数据库失败仍终止启动，避免带病发号。
            if (!isAlreadyExists(e) && !isDuplicateKey(e)) {
                throw e;
            }
        }
    }

    private boolean isDuplicateKey(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("duplicate entry") || lower.contains("unique constraint")
                        || lower.contains("duplicate key") || lower.contains("primary key")) {
                    return true;
                }
            }
            if (current instanceof java.sql.SQLException sqlException) {
                int errorCode = sqlException.getErrorCode();
                if (errorCode == 1062 || errorCode == 23505) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isAlreadyExists(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("already exists") || lower.contains("1050")) {
                    return true;
                }
            }
            if (current instanceof java.sql.SQLException sqlException) {
                String state = sqlException.getSQLState();
                if ("42S01".equals(state) || "42101".equals(state)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
