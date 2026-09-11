package com.bufferblock.migration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 挡块盘点差异闭环（V4）存量数据库迁移。
 *
 * <p>与 V2/V3 相同的背景：{@code ddl-auto=none} 且 MySQL 仅在数据目录为空时执行
 * {@code docker-entrypoint-initdb.d} 脚本，存量数据卷升级需要由应用在
 * Web 服务接受请求前自动补齐结构。本迁移幂等可重复执行：</p>
 * <ol>
 *     <li>创建盘点批次表 stocktake_batch（含冗余统计计数，列表无需聚合明细）；</li>
 *     <li>创建盘点明细表 stocktake_item（应盘快照 + 逐项录入 + 差异标记/处理）；</li>
 *     <li>创建盘点批次号发号器表 stocktake_sequence 并初始化 LOCK 行。</li>
 * </ol>
 *
 * <p>迁移只补盘点模块结构，不改动任何既有挡块档案、产线绑定与移交数据。
 * H2 测试环境由 Hibernate ddl-auto=update 先建表，本迁移全部成为空操作。</p>
 */
@Component
public class StocktakeSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(StocktakeSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public StocktakeSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        log.info("[V4迁移] 开始检查挡块盘点差异闭环所需的数据库结构...");
        createBatchTableIfMissing();
        createItemTableIfMissing();
        createSequenceTableIfMissing();
        ensureInitializationLock();
        log.info("[V4迁移] 盘点模块数据库结构检查完成");
    }

    private void createBatchTableIfMissing() {
        if (tableExists("stocktake_batch")) {
            return;
        }
        try {
            log.info("[V4迁移] 创建盘点批次表 stocktake_batch");
            jdbcTemplate.execute(
                    "CREATE TABLE stocktake_batch (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    batch_no VARCHAR(50) NOT NULL UNIQUE COMMENT '盘点批次号 PD-yyyyMMdd-NNN'," +
                    "    line_id BIGINT NOT NULL COMMENT '盘点产线ID'," +
                    "    stocktake_date DATE NOT NULL COMMENT '盘点日期'," +
                    "    operator VARCHAR(50) NOT NULL COMMENT '盘点负责人'," +
                    "    remark VARCHAR(500) DEFAULT NULL COMMENT '批次备注'," +
                    "    status VARCHAR(20) NOT NULL DEFAULT 'COUNTING' COMMENT '状态: COUNTING-盘点中, COMPLETED-已完成'," +
                    "    total_count INT NOT NULL DEFAULT 0 COMMENT '应盘数量(快照)'," +
                    "    counted_count INT NOT NULL DEFAULT 0 COMMENT '已盘数量(去重)'," +
                    "    discrepancy_count INT NOT NULL DEFAULT 0 COMMENT '差异总数'," +
                    "    pending_count INT NOT NULL DEFAULT 0 COMMENT '待处理差异数'," +
                    "    finish_time DATETIME DEFAULT NULL COMMENT '封账时间'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    UNIQUE INDEX uk_batch_no (batch_no)," +
                    "    INDEX idx_line_id (line_id)," +
                    "    INDEX idx_stocktake_date (stocktake_date)," +
                    "    INDEX idx_status (status)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块盘点批次'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V4迁移] 盘点批次表已存在，跳过");
        }
    }

    private void createItemTableIfMissing() {
        if (tableExists("stocktake_item")) {
            return;
        }
        try {
            log.info("[V4迁移] 创建盘点明细表 stocktake_item");
            jdbcTemplate.execute(
                    "CREATE TABLE stocktake_item (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    batch_id BIGINT NOT NULL COMMENT '盘点批次ID'," +
                    "    block_id BIGINT DEFAULT NULL COMMENT '挡块ID(账外编号可空)'," +
                    "    block_code VARCHAR(50) DEFAULT NULL COMMENT '挡块编号快照'," +
                    "    expected_line_id BIGINT DEFAULT NULL COMMENT '应盘产线ID'," +
                    "    bound_line_id BIGINT DEFAULT NULL COMMENT '录入时当前绑定产线ID'," +
                    "    site_line_id BIGINT DEFAULT NULL COMMENT '现场盘点产线ID'," +
                    "    is_extra TINYINT NOT NULL DEFAULT 0 COMMENT '是否盘盈: 1-是, 0-否(应盘快照)'," +
                    "    is_counted TINYINT NOT NULL DEFAULT 0 COMMENT '是否已盘: 1-是, 0-否'," +
                    "    physical_status VARCHAR(20) DEFAULT NULL COMMENT '实物状态: NORMAL-正常, DAMAGED-损坏, SCRAPPED-报废'," +
                    "    repeat_count INT NOT NULL DEFAULT 0 COMMENT '重复盘点次数(首盘为1)'," +
                    "    count_operator VARCHAR(50) DEFAULT NULL COMMENT '盘点人'," +
                    "    count_time DATETIME DEFAULT NULL COMMENT '盘点时间'," +
                    "    count_remark VARCHAR(500) DEFAULT NULL COMMENT '现场备注'," +
                    "    discrepancy_type VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '差异类型: NONE/MISSING/WRONG_LINE/DUPLICATE/EXTRA/DAMAGED/SCRAPPED'," +
                    "    discrepancy_status VARCHAR(20) DEFAULT NULL COMMENT '处理状态: PENDING-待处理, CONFIRMED-已确认, IGNORED-已忽略'," +
                    "    handle_operator VARCHAR(50) DEFAULT NULL COMMENT '差异处理人'," +
                    "    handle_note VARCHAR(500) DEFAULT NULL COMMENT '处理说明'," +
                    "    handle_time DATETIME DEFAULT NULL COMMENT '处理时间'," +
                    "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "    UNIQUE INDEX uk_batch_block (batch_id, block_id)," +
                    "    INDEX idx_batch_id (batch_id)," +
                    "    INDEX idx_block_id (block_id)," +
                    "    INDEX idx_discrepancy_type (discrepancy_type)," +
                    "    INDEX idx_discrepancy_status (discrepancy_status)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块盘点明细与差异'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V4迁移] 盘点明细表已存在，跳过");
        }
    }

    private void createSequenceTableIfMissing() {
        if (tableExists("stocktake_sequence")) {
            return;
        }
        try {
            log.info("[V4迁移] 创建盘点批次号发号器表 stocktake_sequence");
            jdbcTemplate.execute(
                    "CREATE TABLE stocktake_sequence (" +
                    "    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd'," +
                    "    current_value INT NOT NULL COMMENT '已分配的最大流水号'" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点批次号按日期发号器'");
        } catch (Exception e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
            log.info("[V4迁移] 盘点发号器表已存在，跳过");
        }
    }

    private void ensureInitializationLock() {
        try {
            jdbcTemplate.update(
                    "INSERT INTO stocktake_sequence (sequence_date, current_value) VALUES ('LOCK', 0)");
            log.info("[V4迁移] 已初始化盘点发号器并发建号锁");
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
            log.debug("[V4迁移] 表元数据查询失败，将直接尝试建表：{}", e.getMessage());
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
