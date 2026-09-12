package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 缓冲挡块借用预约（V5）存量数据库迁移。
 *
 * <p>背景与 V2/V3/V4 一致：{@code ddl-auto=none} 且 MySQL 仅在数据目录为空时执行
 * {@code docker-entrypoint-initdb.d} 脚本，存量数据卷升级需要由应用在
 * Web 服务接受请求前自动补齐结构。本迁移幂等可重复执行：</p>
 * <ol>
 *     <li>创建借用预约主表 block_borrow_reservation（含取消原因、提醒计数等字段）；</li>
 *     <li>创建预约单号发号器表 block_borrow_sequence 并初始化 LOCK 行；</li>
 *     <li>创建预约流转记录表 block_borrow_flow_record（预约/取走/归还/取消/逾时提醒）。</li>
 * </ol>
 *
 * <p>迁移只新增借用模块结构，不改动任何既有挡块档案、产线绑定、移交与盘点数据。
 * H2 测试环境由 Hibernate ddl-auto=update 先建表，本迁移全部成为空操作。</p>
 */
@Component
public class BorrowReservationSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(BorrowReservationSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public BorrowReservationSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        log.info("[V5迁移] 开始检查挡块借用预约所需的数据库结构...");
        createReservationTableIfMissing();
        // 存量库（V5 初版无实际归还点列）补齐：归还时允许改归还点
        addColumnIfMissing("block_borrow_reservation", "actual_return_point",
                "ALTER TABLE block_borrow_reservation ADD COLUMN actual_return_point VARCHAR(200) DEFAULT NULL " +
                        "COMMENT '实际归还点（归还时可改，留空沿用约定归还点）'");
        createSequenceTableIfMissing();
        createFlowRecordTableIfMissing();
        ensureInitializationLock();
        log.info("[V5迁移] 借用预约模块数据库结构检查完成");
    }

    private void createReservationTableIfMissing() {
        if (tableExists("block_borrow_reservation")) {
            return;
        }
        try {
            log.info("[V5迁移] 创建借用预约主表 block_borrow_reservation");
            jdbcTemplate.execute(
                    "CREATE TABLE block_borrow_reservation (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    reservation_no VARCHAR(50) NOT NULL UNIQUE COMMENT '预约单号 BR-yyyyMMdd-NNN'," +
                    "    block_id BIGINT NOT NULL COMMENT '挡块ID'," +
                    "    team_name VARCHAR(100) NOT NULL COMMENT '借用班组'," +
                    "    pickup_time DATETIME NOT NULL COMMENT '约定取用时间'," +
                    "    planned_return_time DATETIME DEFAULT NULL COMMENT '计划归还时间'," +
                    "    return_point VARCHAR(200) NOT NULL COMMENT '归还点'," +
                    "    contact_person VARCHAR(50) DEFAULT NULL COMMENT '班组联系人'," +
                    "    contact_phone VARCHAR(30) DEFAULT NULL COMMENT '联系电话'," +
                    "    purpose VARCHAR(500) DEFAULT NULL COMMENT '借用用途'," +
                    "    remark VARCHAR(500) DEFAULT NULL COMMENT '备注'," +
                    "    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED' COMMENT '状态: RESERVED-已预约, PICKED_UP-已取走, RETURNED-已归还, CANCELLED-已取消, OVERDUE-逾时未取'," +
                    "    pickup_operator VARCHAR(50) DEFAULT NULL COMMENT '取走登记人'," +
                    "    actual_pickup_time DATETIME DEFAULT NULL COMMENT '实际取走时间'," +
                    "    return_operator VARCHAR(50) DEFAULT NULL COMMENT '归还登记人'," +
                    "    actual_return_time DATETIME DEFAULT NULL COMMENT '实际归还时间'," +
                    "    actual_return_point VARCHAR(200) DEFAULT NULL COMMENT '实际归还点（归还时可改，留空沿用约定归还点）'," +
                    "    cancel_reason VARCHAR(500) DEFAULT NULL COMMENT '取消原因(必填)'," +
                    "    cancel_operator VARCHAR(50) DEFAULT NULL COMMENT '取消操作人'," +
                    "    cancel_time DATETIME DEFAULT NULL COMMENT '取消时间'," +
                    "    remind_count INT NOT NULL DEFAULT 0 COMMENT '逾时未取提醒次数'," +
                    "    last_remind_time DATETIME DEFAULT NULL COMMENT '最近一次提醒时间'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    UNIQUE INDEX uk_borrow_no (reservation_no)," +
                    "    INDEX idx_borrow_block (block_id)," +
                    "    INDEX idx_borrow_team (team_name)," +
                    "    INDEX idx_borrow_pickup (pickup_time)," +
                    "    INDEX idx_borrow_status (status)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缓冲挡块借用预约'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V5迁移] 借用预约主表已存在，跳过");
        }
    }

    /** 存量库幂等补列：列已存在则跳过；元数据不可读时兜底捕获 1060/重复列异常。 */
    private void addColumnIfMissing(String table, String column, String ddl) {
        if (columnExists(table, column)) {
            return;
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

    private boolean columnExists(String table, String column) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)",
                    Integer.class, table, column);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("[V5迁移] 列元数据查询失败，将直接尝试加列：{}", e.getMessage());
            return false;
        }
    }

    private void createSequenceTableIfMissing() {
        if (tableExists("block_borrow_sequence")) {
            return;
        }
        try {
            log.info("[V5迁移] 创建借用预约发号器表 block_borrow_sequence");
            jdbcTemplate.execute(
                    "CREATE TABLE block_borrow_sequence (" +
                    "    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd'," +
                    "    current_value INT NOT NULL COMMENT '已分配的最大流水号'" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='借用预约单号按日期发号器'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V5迁移] 借用预约发号器表已存在，跳过");
        }
    }

    private void createFlowRecordTableIfMissing() {
        if (tableExists("block_borrow_flow_record")) {
            return;
        }
        try {
            log.info("[V5迁移] 创建借用预约流转记录表 block_borrow_flow_record");
            jdbcTemplate.execute(
                    "CREATE TABLE block_borrow_flow_record (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    reservation_id BIGINT NOT NULL COMMENT '预约单ID'," +
                    "    action VARCHAR(30) NOT NULL COMMENT '动作: BOOK-预约, PICKUP-取走, RETURN-归还, CANCEL-取消, OVERDUE_REMIND-逾时提醒'," +
                    "    from_status VARCHAR(20) DEFAULT NULL COMMENT '变更前状态'," +
                    "    to_status VARCHAR(20) DEFAULT NULL COMMENT '变更后状态'," +
                    "    operator VARCHAR(50) DEFAULT NULL COMMENT '操作人'," +
                    "    note VARCHAR(500) DEFAULT NULL COMMENT '处理说明/原因'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    INDEX idx_borrow_flow_reservation (reservation_id)," +
                    "    INDEX idx_borrow_flow_action (action)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='借用预约流转记录'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V5迁移] 借用预约流转记录表已存在，跳过");
        }
    }

    private void ensureInitializationLock() {
        try {
            jdbcTemplate.update(
                    "INSERT INTO block_borrow_sequence (sequence_date, current_value) VALUES ('LOCK', 0)");
            log.info("[V5迁移] 已初始化借用预约发号器并发建号锁");
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
            log.debug("[V5迁移] 表元数据查询失败，将直接尝试建表：{}", e.getMessage());
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
                if ("42S01".equals(state) || "42101".equals(state) || "42S21".equals(state)) {
                    return true;
                }
                // 1060: MySQL Duplicate column name
                if (sqlEx.getErrorCode() == 1060) {
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
