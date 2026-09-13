package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 点检工装校准台（V8）存量数据库迁移。
 *
 * <p>新增卡尺 / 塞尺 / 百分表台账表 {@code gauge_tool} 与工装校准记录表
 * {@code gauge_calibration}，不改动任何既有表与挡块数据。
 * 迁移幂等可重复执行，重复启动安全；H2 测试环境由 Hibernate ddl-auto=update 先建表，
 * 本迁移成为空操作。</p>
 */
@Component
public class GaugeCalibrationSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(GaugeCalibrationSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public GaugeCalibrationSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        // H2 测试环境由 Hibernate ddl-auto=update 依据实体先建表，
        // 建表 DDL 使用 MySQL 专有语法（TINYINT/内联索引），这里直接跳过
        try {
            String product = jdbcTemplate.execute((java.sql.Connection conn) ->
                    conn.getMetaData().getDatabaseProductName());
            if (product != null && product.toUpperCase().contains("H2")) {
                log.info("[V8迁移] 检测到 H2 测试环境，工装表结构由 Hibernate 维护，跳过建表迁移");
                return;
            }
        } catch (Exception e) {
            log.debug("[V8迁移] 数据库产品名探测失败，继续按 MySQL 路径检查：{}", e.getMessage());
        }
        log.info("[V8迁移] 开始检查点检工装校准台所需的数据库结构...");
        createGaugeToolTableIfMissing();
        createGaugeCalibrationTableIfMissing();
        if (tableExists("gauge_tool")) {
            addIndexIfMissing("gauge_tool", "idx_gauge_code",
                    "CREATE INDEX idx_gauge_code ON gauge_tool (tool_code)");
            addIndexIfMissing("gauge_tool", "idx_gauge_due",
                    "CREATE INDEX idx_gauge_due ON gauge_tool (calibration_due_date)");
        }
        if (tableExists("gauge_calibration")) {
            addIndexIfMissing("gauge_calibration", "idx_gauge_cal_tool",
                    "CREATE INDEX idx_gauge_cal_tool ON gauge_calibration (tool_id)");
            addIndexIfMissing("gauge_calibration", "idx_gauge_cal_date",
                    "CREATE INDEX idx_gauge_cal_date ON gauge_calibration (calibration_date)");
        }
        log.info("[V8迁移] 点检工装校准台数据库结构检查完成");
    }

    private void createGaugeToolTableIfMissing() {
        if (tableExists("gauge_tool")) {
            return;
        }
        try {
            log.info("[V8迁移] 创建点检工装台账表 gauge_tool");
            jdbcTemplate.execute(
                    "CREATE TABLE gauge_tool (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    tool_code VARCHAR(50) NOT NULL COMMENT '工装编号'," +
                    "    tool_type VARCHAR(30) NOT NULL COMMENT '工装类型: CALIPER-卡尺, FEELER-塞尺, DIAL_INDICATOR-百分表'," +
                    "    spec_model VARCHAR(100) DEFAULT NULL COMMENT '规格型号'," +
                    "    calibration_due_date DATE NOT NULL COMMENT '校准到期日（合格校准时按下次应校日同步）'," +
                    "    keeper_team VARCHAR(100) NOT NULL COMMENT '保管班组'," +
                    "    remark VARCHAR(500) DEFAULT NULL COMMENT '备注'," +
                    "    disabled TINYINT NOT NULL DEFAULT 0 COMMENT '停用标记: 0-在期, 1-停用'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    UNIQUE INDEX uk_gauge_code (tool_code)," +
                    "    INDEX idx_gauge_due (calibration_due_date)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点检工装台账（卡尺/塞尺/百分表）'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V8迁移] 工装台账表已存在，跳过");
        }
    }

    private void createGaugeCalibrationTableIfMissing() {
        if (tableExists("gauge_calibration")) {
            return;
        }
        try {
            log.info("[V8迁移] 创建点检工装校准记录表 gauge_calibration");
            jdbcTemplate.execute(
                    "CREATE TABLE gauge_calibration (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    tool_id BIGINT NOT NULL COMMENT '工装ID'," +
                    "    calibration_date DATE NOT NULL COMMENT '校准日期'," +
                    "    result VARCHAR(20) NOT NULL COMMENT '校准结论: PASS-合格, FAIL-不合格'," +
                    "    valid_until DATE NOT NULL COMMENT '本次校准有效期至'," +
                    "    next_due_date DATE NOT NULL COMMENT '下次应校日期'," +
                    "    calibrator VARCHAR(50) NOT NULL COMMENT '校准人'," +
                    "    note VARCHAR(500) DEFAULT NULL COMMENT '校准备注'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    INDEX idx_gauge_cal_tool (tool_id)," +
                    "    INDEX idx_gauge_cal_date (calibration_date)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点检工装校准记录'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V8迁移] 工装校准记录表已存在，跳过");
        }
    }

    private boolean tableExists(String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                    Integer.class, table);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[V8迁移] 表元数据查询失败，将直接尝试建表：{}", e.getMessage());
            return false;
        }
    }

    private void addIndexIfMissing(String table, String indexName, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                            "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(INDEX_NAME) = LOWER(?)",
                    Integer.class, table, indexName);
            if (count != null && count > 0) {
                return;
            }
        } catch (Exception e) {
            log.debug("[V8迁移] 索引元数据查询失败，将直接尝试建索引：{}", e.getMessage());
        }
        try {
            log.info("[V8迁移] 补充缺失索引：{}.{}", table, indexName);
            jdbcTemplate.execute(ddl);
        } catch (Exception e) {
            if (isAlreadyExists(e)) {
                log.info("[V8迁移] 索引 {} 已存在，跳过", indexName);
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
