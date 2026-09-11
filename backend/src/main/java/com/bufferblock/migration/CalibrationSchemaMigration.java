package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 挡块校准管理（V5）存量数据库迁移。
 *
 * <p>与 V2/V3/V4 背景相同：{@code ddl-auto=none} 且 MySQL 仅在数据目录为空时执行
 * init 脚本，存量数据卷升级需要由应用在 Web 服务接受请求前自动补齐结构。
 * 本迁移幂等可重复执行：</p>
 * <ol>
 *     <li>为 buffer_block 增加校准周期、在用/挂起状态、挂起原因与挂起时间列；</li>
 *     <li>将已绑定产线的存量挡块校准周期回填为 12 个月（NULL 周期一并归一化）；</li>
 *     <li>创建 block_calibration 校准记录表。</li>
 * </ol>
 *
 * <p>迁移不改动任何 block_line_binding 产线绑定与 block_transfer 移交数据。
 * H2 测试环境由 Hibernate ddl-auto=update 先建表/加列，本迁移全部成为空操作。</p>
 */
@Component
public class CalibrationSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(CalibrationSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public CalibrationSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        log.info("[V5迁移] 开始检查挡块校准管理所需的数据库结构...");

        addColumnIfMissing("buffer_block", "calibration_cycle_months",
                "ALTER TABLE buffer_block ADD COLUMN calibration_cycle_months INT DEFAULT 12 COMMENT '校准周期(月)'");
        addColumnIfMissing("buffer_block", "service_status",
                "ALTER TABLE buffer_block ADD COLUMN service_status VARCHAR(20) DEFAULT 'IN_SERVICE' COMMENT '在用状态: IN_SERVICE-在用, SUSPENDED-挂起待修'");
        addColumnIfMissing("buffer_block", "suspend_reason",
                "ALTER TABLE buffer_block ADD COLUMN suspend_reason VARCHAR(500) DEFAULT NULL COMMENT '挂起原因'");
        addColumnIfMissing("buffer_block", "suspend_time",
                "ALTER TABLE buffer_block ADD COLUMN suspend_time DATETIME DEFAULT NULL COMMENT '挂起时间'");

        backfillCycleAndStatus();
        createCalibrationTableIfMissing();
        addIndexIfMissing("block_calibration", "idx_cal_block",
                "CREATE INDEX idx_cal_block ON block_calibration (block_id)");
        addIndexIfMissing("block_calibration", "idx_cal_due",
                "CREATE INDEX idx_cal_due ON block_calibration (next_due_date)");

        log.info("[V5迁移] 挡块校准管理数据库结构检查完成");
    }

    private void backfillCycleAndStatus() {
        int cycles = jdbcTemplate.update(
                "UPDATE buffer_block SET calibration_cycle_months = 12 " +
                "WHERE calibration_cycle_months IS NULL OR calibration_cycle_months <= 0");
        if (cycles > 0) {
            log.info("[V5迁移] 已为 {} 个存量挡块回填默认校准周期 12 个月", cycles);
        }
        int statuses = jdbcTemplate.update(
                "UPDATE buffer_block SET service_status = 'IN_SERVICE' " +
                "WHERE service_status IS NULL OR service_status = ''");
        if (statuses > 0) {
            log.info("[V5迁移] 已归一化 {} 个存量挡块在用状态为 IN_SERVICE", statuses);
        }
    }

    private void createCalibrationTableIfMissing() {
        if (tableExists("block_calibration")) {
            return;
        }
        try {
            log.info("[V5迁移] 创建校准记录表 block_calibration");
            jdbcTemplate.execute(
                    "CREATE TABLE block_calibration (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    block_id BIGINT NOT NULL COMMENT '挡块ID'," +
                    "    calibration_date DATE NOT NULL COMMENT '校准日期'," +
                    "    result VARCHAR(20) NOT NULL COMMENT '校准结论: PASS-合格, FAIL-不合格'," +
                    "    valid_until DATE NOT NULL COMMENT '本次校准有效期至'," +
                    "    next_due_date DATE NOT NULL COMMENT '下次应校日期'," +
                    "    cycle_months INT DEFAULT NULL COMMENT '本次校准周期(月)'," +
                    "    calibrator VARCHAR(50) NOT NULL COMMENT '校准人'," +
                    "    note VARCHAR(500) DEFAULT NULL COMMENT '校准备注/不合格说明'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    INDEX idx_cal_block (block_id)," +
                    "    INDEX idx_cal_date (calibration_date)," +
                    "    INDEX idx_cal_result (result)," +
                    "    INDEX idx_cal_due (next_due_date)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块校准记录'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V5迁移] 校准记录表已存在，跳过");
        }
    }

    private boolean tableExists(String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                    Integer.class, table);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[V5迁移] 表元数据查询失败，将直接尝试建表：{}", e.getMessage());
            return false;
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        try {
            if (columnExists(table, column)) {
                return;
            }
        } catch (Exception e) {
            log.debug("[V5迁移] 列元数据查询失败，将直接尝试加列：{}", e.getMessage());
        }
        try {
            log.info("[V5迁移] 补充缺失列：{}.{}", table, column);
            jdbcTemplate.execute(ddl);
        } catch (Exception e) {
            if (isAlreadyExists(e)) {
                log.info("[V5迁移] 列 {}.{} 已存在，跳过", table, column);
            } else {
                throw e;
            }
        }
    }

    private boolean indexExists(String table, String indexName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                    "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(INDEX_NAME) = LOWER(?)",
                    Integer.class, table, indexName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[V5迁移] 索引元数据查询失败，将直接尝试建索引：{}", e.getMessage());
            return false;
        }
    }

    private void addIndexIfMissing(String table, String indexName, String ddl) {
        try {
            if (indexExists(table, indexName)) {
                return;
            }
        } catch (Exception e) {
            log.debug("[V5迁移] 索引元数据查询失败，将直接尝试建索引：{}", e.getMessage());
        }
        try {
            log.info("[V5迁移] 补充缺失索引：{}.{}", table, indexName);
            jdbcTemplate.execute(ddl);
        } catch (Exception e) {
            if (isAlreadyExists(e)) {
                log.info("[V5迁移] 索引 {} 已存在，跳过", indexName);
            } else {
                throw e;
            }
        }
    }

    private boolean isAlreadyExists(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("already exists") || lower.contains("1050")
                        || lower.contains("1060") || lower.contains("1061")) {
                    return true;
                }
            }
            if (cur instanceof java.sql.SQLException sqlEx) {
                String state = sqlEx.getSQLState();
                if ("42S01".equals(state) || "42101".equals(state)
                        || "42S11".equals(state) || "42111".equals(state) || "42S21".equals(state)) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }
}
