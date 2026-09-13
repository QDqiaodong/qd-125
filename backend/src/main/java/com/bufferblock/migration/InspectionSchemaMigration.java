package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 挡块班次点检台账（V7）存量数据库迁移。
 *
 * <p>与 V2~V6 背景相同：{@code ddl-auto=none} 且 MySQL 仅在数据目录为空时执行
 * init 脚本，存量数据卷升级需要由应用在 Web 服务接受请求前自动补齐结构。
 * 本迁移幂等可重复执行，只创建 block_inspection 点检记录表及其索引，
 * 不改动任何 buffer_block 产线绑定、状态与既有业务数据。
 * H2 测试环境由 Hibernate ddl-auto=update 先建表，本迁移成为空操作。</p>
 */
@Component
public class InspectionSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(InspectionSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public InspectionSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        log.info("[V7迁移] 开始检查挡块班次点检台账所需的数据库结构...");
        createInspectionTableIfMissing();
        if (tableExists("block_inspection")) {
            addIndexIfMissing("block_inspection", "idx_insp_block",
                    "CREATE INDEX idx_insp_block ON block_inspection (block_id)");
            addIndexIfMissing("block_inspection", "idx_insp_time",
                    "CREATE INDEX idx_insp_time ON block_inspection (inspection_time)");
            addIndexIfMissing("block_inspection", "idx_insp_result",
                    "CREATE INDEX idx_insp_result ON block_inspection (result)");
            addIndexIfMissing("block_inspection", "idx_insp_shift",
                    "CREATE INDEX idx_insp_shift ON block_inspection (shift_code)");
        }
        log.info("[V7迁移] 挡块班次点检台账数据库结构检查完成");
    }

    private void createInspectionTableIfMissing() {
        if (tableExists("block_inspection")) {
            return;
        }
        try {
            log.info("[V7迁移] 创建班次点检记录表 block_inspection");
            jdbcTemplate.execute(
                    "CREATE TABLE block_inspection (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    block_id BIGINT NOT NULL COMMENT '挡块ID'," +
                    "    inspection_time DATETIME NOT NULL COMMENT '点检打卡时刻'," +
                    "    shift_code VARCHAR(20) NOT NULL COMMENT '点检班次: MORNING-早班, AFTERNOON-中班, NIGHT-晚班'," +
                    "    inspector VARCHAR(50) NOT NULL COMMENT '点检人'," +
                    "    result VARCHAR(20) NOT NULL COMMENT '点检结论: USABLE-可用, UNUSABLE-不可用'," +
                    "    note VARCHAR(500) DEFAULT NULL COMMENT '点检备注/不可用情况说明'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    INDEX idx_insp_block (block_id)," +
                    "    INDEX idx_insp_time (inspection_time)," +
                    "    INDEX idx_insp_result (result)," +
                    "    INDEX idx_insp_shift (shift_code)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块班次点检记录'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V7迁移] 点检记录表已存在，跳过");
        }
    }

    private boolean tableExists(String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                    Integer.class, table);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[V7迁移] 表元数据查询失败，将直接尝试建表：{}", e.getMessage());
            return false;
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
            log.debug("[V7迁移] 索引元数据查询失败，将直接尝试建索引：{}", e.getMessage());
            return false;
        }
    }

    private void addIndexIfMissing(String table, String indexName, String ddl) {
        try {
            if (indexExists(table, indexName)) {
                return;
            }
        } catch (Exception e) {
            log.debug("[V7迁移] 索引元数据查询失败，将直接尝试建索引：{}", e.getMessage());
        }
        try {
            log.info("[V7迁移] 补充缺失索引：{}.{}", table, indexName);
            jdbcTemplate.execute(ddl);
        } catch (Exception e) {
            if (isAlreadyExists(e)) {
                log.info("[V7迁移] 索引 {} 已存在，跳过", indexName);
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
