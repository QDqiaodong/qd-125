package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 移交确认流程（V2）存量数据库迁移。
 *
 * <p>背景：MySQL 镜像只会在数据目录为空（首次初始化）时执行
 * {@code /docker-entrypoint-initdb.d} 下的脚本；使用存量 MySQL 数据卷升级时，
 * {@code z_V2_transfer_confirm.sql} 不会被执行，而工程又采用 {@code ddl-auto=none}，
 * 导致历史库缺少 V2 新增列与流转记录表，应用启动后查询/确认页面直接 500。</p>
 *
 * <p>本组件在 Spring 容器刷新阶段（Web 容器开始接受请求之前）用 JdbcTemplate
 * 执行一次幂等迁移，全新库为空操作，存量库自动补齐结构并回填历史数据，
 * 可重复执行：</p>
 * <ol>
 *     <li>补齐 block_transfer 的 V2 列与状态索引；</li>
 *     <li>创建 transfer_flow_record 流转记录表；</li>
 *     <li>升级前登记、按旧流程已即时完成绑定的历史单据（尚无任何流转记录）
 *         统一回填为“已确认”，并补登 REGISTER/CONFIRM 流转记录；</li>
 *     <li>新流程下登记的 PENDING 单据（已有 REGISTER 流转记录）保持待确认，
 *         仍可在移交确认页查询、确认或驳回。</li>
 * </ol>
 *
 * <p>迁移只补结构与流转数据，<b>不</b>重放/改动 block_line_binding 产线绑定，
 * 原有的移交登记与确认后绑定逻辑不受影响。</p>
 */
@Component
public class TransferConfirmSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(TransferConfirmSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public TransferConfirmSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        log.info("[V2迁移] 开始检查移交确认流程所需的数据库结构...");

        addColumnIfMissing("block_transfer", "status",
                "ALTER TABLE block_transfer ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待确认, CONFIRMED-已确认, REJECTED-已驳回'");
        addColumnIfMissing("block_transfer", "handle_note",
                "ALTER TABLE block_transfer ADD COLUMN handle_note VARCHAR(500) DEFAULT NULL COMMENT '接收方处理说明'");
        addColumnIfMissing("block_transfer", "handle_time",
                "ALTER TABLE block_transfer ADD COLUMN handle_time DATETIME DEFAULT NULL COMMENT '接收方处理时间'");
        addColumnIfMissing("block_transfer", "receipt_print_count",
                "ALTER TABLE block_transfer ADD COLUMN receipt_print_count INT DEFAULT 0 COMMENT '确认回执打印次数'");
        addColumnIfMissing("block_transfer", "last_receipt_print_time",
                "ALTER TABLE block_transfer ADD COLUMN last_receipt_print_time DATETIME DEFAULT NULL COMMENT '回执最后打印时间'");

        addIndexIfMissing("block_transfer", "idx_status",
                "CREATE INDEX idx_status ON block_transfer (status)");

        createFlowRecordTableIfMissing();

        backfillLegacyTransfers();
        backfillFlowRecords();

        log.info("[V2迁移] 数据库结构检查与历史数据兼容处理完成");
    }

    private boolean columnExists(String table, String column) {
        // 表名/列名统一按小写比较，避免不同数据库或 lower_case_table_names 配置下大小写差异；
        // 不限定 TABLE_SCHEMA，以兼容 MySQL 与内嵌测试库的元数据差异（应用只连接单一业务库）。
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        if (safeColumnExists(table, column)) {
            return;
        }
        try {
            log.info("[V2迁移] 补充缺失列：{}.{}", table, column);
            jdbcTemplate.execute(ddl);
        } catch (Exception e) {
            // 元数据不可读等极端情况下，若列实际已存在（MySQL 1060 Duplicate column name）则视为成功
            if (isAlreadyExistsError(e)) {
                log.info("[V2迁移] 列 {}.{} 已存在，跳过", table, column);
            } else {
                throw e;
            }
        }
    }

    private boolean safeColumnExists(String table, String column) {
        try {
            return columnExists(table, column);
        } catch (Exception e) {
            log.debug("[V2迁移] 列元数据查询失败，将直接尝试加列：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断异常链上是否为“列/索引/表已存在”。
     * 同时兼容 MySQL（1060 Duplicate column / 1061 Duplicate key / 1050 Table exists）
     * 与 H2（42121/42111/42101，消息含 already exists），并覆盖 Spring 转译后顶层消息
     * 丢失明细的情况——逐层遍历 cause 并检查 java.sql.SQLException 的 SQLState。
     */
    private boolean isAlreadyExistsError(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("duplicate column") || lower.contains("duplicate key")
                        || lower.contains("already exists")
                        || lower.contains("1060") || lower.contains("1061") || lower.contains("1050")) {
                    return true;
                }
            }
            if (cur instanceof java.sql.SQLException sqlEx) {
                String state = sqlEx.getSQLState();
                if ("42S21".equals(state) || "42S11".equals(state) || "42S01".equals(state)
                        || "42121".equals(state) || "42111".equals(state) || "42101".equals(state)) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private boolean indexExists(String table, String indexName) {
        // MySQL 8 在 information_schema.STATISTICS 中暴露索引元数据
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(INDEX_NAME) = LOWER(?)",
                Integer.class, table, indexName);
        return count != null && count > 0;
    }

    private void addIndexIfMissing(String table, String indexName, String ddl) {
        // 优先查元数据；极端情况下元数据不可读时，捕获“索引已存在”错误兜底，保证幂等
        try {
            if (indexExists(table, indexName)) {
                return;
            }
        } catch (Exception e) {
            log.debug("[V2迁移] 索引元数据查询失败，将直接尝试建索引：{}", e.getMessage());
        }
        try {
            log.info("[V2迁移] 补充缺失索引：{}.{}", table, indexName);
            jdbcTemplate.execute(ddl);
        } catch (Exception e) {
            // 已存在视为成功：MySQL 1061 Duplicate key name / H2 42111 Index already exists
            if (isAlreadyExistsError(e)) {
                log.info("[V2迁移] 索引 {} 已存在，跳过", indexName);
            } else {
                throw e;
            }
        }
    }

    private void createFlowRecordTableIfMissing() {
        try {
            if (tableExists("transfer_flow_record")) {
                return;
            }
        } catch (Exception e) {
            log.debug("[V2迁移] 表元数据查询失败，将直接尝试建表：{}", e.getMessage());
        }
        try {
            log.info("[V2迁移] 创建流转记录表 transfer_flow_record");
            jdbcTemplate.execute(
                    "CREATE TABLE transfer_flow_record (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    transfer_id BIGINT NOT NULL COMMENT '移交单ID'," +
                    "    action VARCHAR(30) NOT NULL COMMENT '动作: REGISTER-登记, CONFIRM-确认, REJECT-驳回, PRINT_RECEIPT-打印回执, PRINT_ORDER-打印移交单'," +
                    "    from_status VARCHAR(20) DEFAULT NULL COMMENT '变更前状态'," +
                    "    to_status VARCHAR(20) DEFAULT NULL COMMENT '变更后状态'," +
                    "    operator VARCHAR(50) DEFAULT NULL COMMENT '操作人'," +
                    "    note VARCHAR(500) DEFAULT NULL COMMENT '处理说明/备注'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    INDEX idx_transfer_id (transfer_id)," +
                    "    INDEX idx_action (action)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='移交确认流转记录'");
        } catch (Exception e) {
            // 表已存在视为成功（MySQL 1050 / H2 42101）
            if (isAlreadyExistsError(e)) {
                log.info("[V2迁移] 流转记录表已存在，跳过");
            } else {
                throw e;
            }
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                Integer.class, table);
        return count != null && count > 0;
    }

    /**
     * 历史单据兼容：升级前没有流转记录表，旧流程登记即完成产线绑定。
     * 仅对“不存在 REGISTER 流转记录”的历史单据回填为已确认，
     * 新流程已登记待确认的单据（有 REGISTER 记录）不受影响。
     */
    private void backfillLegacyTransfers() {
        int updated = jdbcTemplate.update(
                "UPDATE block_transfer t " +
                "SET t.status = 'CONFIRMED', " +
                "    t.handle_note = COALESCE(t.handle_note, CONCAT('历史数据迁移：原流程已完成移交', IFNULL(CONCAT('（', t.remark, '）'), ''))), " +
                "    t.handle_time = COALESCE(t.handle_time, t.update_time) " +
                "WHERE (t.status IS NULL OR t.status = '' OR t.status = 'PENDING') " +
                "  AND NOT EXISTS (SELECT 1 FROM transfer_flow_record r " +
                "                  WHERE r.transfer_id = t.id AND r.action = 'REGISTER')");
        if (updated > 0) {
            log.info("[V2迁移] 已将 {} 条历史移交单回填为已确认（原流程登记即完成绑定，绑定记录不做变更）", updated);
        }

        int normalized = jdbcTemplate.update(
                "UPDATE block_transfer SET status = 'CONFIRMED' WHERE status IS NULL OR status = ''");
        if (normalized > 0) {
            log.info("[V2迁移] 已归一化 {} 条空状态历史记录为已确认", normalized);
        }
    }

    /**
     * 为历史单据补登流转记录，全部按 transfer + action 维度幂等，重复执行不产生重复数据。
     */
    private void backfillFlowRecords() {
        int registered = jdbcTemplate.update(
                "INSERT INTO transfer_flow_record (transfer_id, action, from_status, to_status, operator, note, create_time) " +
                "SELECT t.id, 'REGISTER', NULL, 'PENDING', t.transfer_operator, '移交登记，等待接收方确认', t.create_time " +
                "FROM block_transfer t " +
                "WHERE NOT EXISTS (SELECT 1 FROM transfer_flow_record r WHERE r.transfer_id = t.id AND r.action = 'REGISTER')");
        if (registered > 0) {
            log.info("[V2迁移] 补登 REGISTER 流转记录 {} 条", registered);
        }

        int confirmed = jdbcTemplate.update(
                "INSERT INTO transfer_flow_record (transfer_id, action, from_status, to_status, operator, note, create_time) " +
                "SELECT t.id, 'CONFIRM', 'PENDING', 'CONFIRMED', " +
                "       COALESCE(NULLIF(t.receive_operator, ''), t.transfer_operator), " +
                "       COALESCE(t.handle_note, '历史数据迁移：确认接收'), t.handle_time " +
                "FROM block_transfer t " +
                "WHERE t.status = 'CONFIRMED' " +
                "  AND NOT EXISTS (SELECT 1 FROM transfer_flow_record r WHERE r.transfer_id = t.id AND r.action = 'CONFIRM')");
        if (confirmed > 0) {
            log.info("[V2迁移] 补登 CONFIRM 流转记录 {} 条", confirmed);
        }

        int rejected = jdbcTemplate.update(
                "INSERT INTO transfer_flow_record (transfer_id, action, from_status, to_status, operator, note, create_time) " +
                "SELECT t.id, 'REJECT', 'PENDING', 'REJECTED', " +
                "       COALESCE(NULLIF(t.receive_operator, ''), t.transfer_operator), " +
                "       COALESCE(t.handle_note, '历史数据迁移：驳回'), t.handle_time " +
                "FROM block_transfer t " +
                "WHERE t.status = 'REJECTED' " +
                "  AND NOT EXISTS (SELECT 1 FROM transfer_flow_record r WHERE r.transfer_id = t.id AND r.action = 'REJECT')");
        if (rejected > 0) {
            log.info("[V2迁移] 补登 REJECT 流转记录 {} 条", rejected);
        }
    }
}
