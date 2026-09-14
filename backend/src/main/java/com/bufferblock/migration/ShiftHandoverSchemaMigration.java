package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 班组交班（V6）存量数据库迁移。
 *
 * <p>背景与 V2~V5 一致：{@code ddl-auto=none} 且 MySQL 仅在数据目录为空时执行
 * {@code docker-entrypoint-initdb.d} 脚本，存量数据卷升级需要由应用在
 * Web 服务接受请求前自动补齐结构。本迁移幂等可重复执行：</p>
 * <ol>
 *     <li>创建班组交班主表 shift_handover（交班中/已完成、事项总数与已确认计数冗余）；</li>
 *     <li>创建交班事项表 shift_handover_item（四类未结事项快照（含拦截中点检工装） + 接班人逐条确认落库）；</li>
 *     <li>创建交班单号发号器表 shift_handover_sequence 并初始化 LOCK 行。</li>
 * </ol>
 *
 * <p>迁移只新增交班模块结构，不改动任何既有挡块档案、产线绑定、移交、盘点与借用数据。
 * H2 测试环境由 Hibernate ddl-auto=update 先建表，本迁移全部成为空操作。</p>
 */
@Component
public class ShiftHandoverSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(ShiftHandoverSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public ShiftHandoverSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        log.info("[V6迁移] 开始检查班组交班所需的数据库结构...");
        createHandoverTableIfMissing();
        createHandoverItemTableIfMissing();
        createSequenceTableIfMissing();
        ensureInitializationLock();
        log.info("[V6迁移] 班组交班模块数据库结构检查完成");
    }

    private void createHandoverTableIfMissing() {
        if (tableExists("shift_handover")) {
            return;
        }
        try {
            log.info("[V6迁移] 创建班组交班主表 shift_handover");
            jdbcTemplate.execute(
                    "CREATE TABLE shift_handover (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    handover_no VARCHAR(50) NOT NULL UNIQUE COMMENT '交班单号 HO-yyyyMMdd-NNN'," +
                    "    from_team VARCHAR(100) NOT NULL COMMENT '交班班组'," +
                    "    to_team VARCHAR(100) NOT NULL COMMENT '接班班组'," +
                    "    handover_operator VARCHAR(50) NOT NULL COMMENT '交班登记人'," +
                    "    receive_operator VARCHAR(50) NOT NULL COMMENT '接班人'," +
                    "    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态: IN_PROGRESS-交班中, COMPLETED-已完成'," +
                    "    total_count INT NOT NULL DEFAULT 0 COMMENT '交班事项总条数(登记时快照)'," +
                    "    confirmed_count INT NOT NULL DEFAULT 0 COMMENT '接班人已确认条数'," +
                    "    remark VARCHAR(500) DEFAULT NULL COMMENT '备注'," +
                    "    finish_time DATETIME DEFAULT NULL COMMENT '交班完成时间'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    UNIQUE INDEX uk_handover_no (handover_no)," +
                    "    INDEX idx_handover_status (status)," +
                    "    INDEX idx_handover_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班组交班主表'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V6迁移] 班组交班主表已存在，跳过");
        }
    }

    private void createHandoverItemTableIfMissing() {
        if (tableExists("shift_handover_item")) {
            return;
        }
        try {
            log.info("[V6迁移] 创建班组交班事项表 shift_handover_item");
            jdbcTemplate.execute(
                    "CREATE TABLE shift_handover_item (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    handover_id BIGINT NOT NULL COMMENT '交班单ID'," +
                    "    item_type VARCHAR(30) NOT NULL COMMENT '事项类型: BORROW_UNRETURNED-未还预约, TRANSFER_PENDING-待确认移交, STOCKTAKE_PENDING-待处理盘点差异, GAUGE_BLOCKED-拦截中点检工装'," +
                    "    ref_id BIGINT DEFAULT NULL COMMENT '源单据ID快照(预约单/移交单/盘点明细/工装台账)'," +
                    "    ref_no VARCHAR(50) DEFAULT NULL COMMENT '源单号快照(BR-/TRF-/PD-/工装编号)'," +
                    "    block_id BIGINT DEFAULT NULL COMMENT '挡块ID快照'," +
                    "    block_code VARCHAR(50) DEFAULT NULL COMMENT '挡块编号快照'," +
                    "    summary VARCHAR(500) DEFAULT NULL COMMENT '事项摘要快照'," +
                    "    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待确认, CONFIRMED-已确认'," +
                    "    confirm_operator VARCHAR(50) DEFAULT NULL COMMENT '确认人(接班人)'," +
                    "    confirm_time DATETIME DEFAULT NULL COMMENT '确认时间'," +
                    "    confirm_note VARCHAR(500) DEFAULT NULL COMMENT '确认备注'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    INDEX idx_handover_item_handover (handover_id)," +
                    "    INDEX idx_handover_item_status (status)," +
                    "    INDEX idx_handover_item_type (item_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班组交班事项'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V6迁移] 班组交班事项表已存在，跳过");
        }
    }

    private void createSequenceTableIfMissing() {
        if (tableExists("shift_handover_sequence")) {
            return;
        }
        try {
            log.info("[V6迁移] 创建班组交班发号器表 shift_handover_sequence");
            jdbcTemplate.execute(
                    "CREATE TABLE shift_handover_sequence (" +
                    "    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd'," +
                    "    current_value INT NOT NULL COMMENT '已分配的最大流水号'" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班组交班单号按日期发号器'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V6迁移] 班组交班发号器表已存在，跳过");
        }
    }

    private void ensureInitializationLock() {
        try {
            jdbcTemplate.update(
                    "INSERT INTO shift_handover_sequence (sequence_date, current_value) VALUES ('LOCK', 0)");
            log.info("[V6迁移] 已初始化班组交班发号器并发建号锁");
        } catch (Exception e) {
            // 已存在锁行即可；只忽略主键/唯一键冲突，其他数据库失败仍终止启动，避免带病发号。
            if (!isDuplicateKey(e)) {
                throw e;
            }
        }
    }

    private boolean tableExists(String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                    Integer.class, table);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[V6迁移] 表元数据查询失败，将直接尝试建表：{}", e.getMessage());
            return false;
        }
    }

    private boolean isAlreadyExists(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("already exists") || lower.contains("1050")) {
                    return true;
                }
            }
            if (cur instanceof java.sql.SQLException sqlEx) {
                String state = sqlEx.getSQLState();
                if ("42S01".equals(state) || "42101".equals(state)) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private boolean isDuplicateKey(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("duplicate entry") || lower.contains("unique constraint")
                        || lower.contains("duplicate key") || lower.contains("primary key")) {
                    return true;
                }
            }
            if (cur instanceof java.sql.SQLException sqlEx) {
                if (sqlEx.getErrorCode() == 1062 || sqlEx.getErrorCode() == 23505) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }
}
