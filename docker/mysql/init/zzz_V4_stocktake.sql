-- ====================================================================
-- 挡块盘点差异闭环模块升级脚本（V4，幂等可重复执行）
-- 全新部署时 schema.sql 已包含全部结构，本脚本执行结果为空操作；
-- 存量部署可手动执行：mysql -uroot -p buffer_block_db < zzz_V4_stocktake.sql
--
-- 说明：/docker-entrypoint-initdb.d 仅在 MySQL 数据目录为空时执行，
--       存量数据卷升级无需手动执行本脚本——新版后端启动时会通过
--       StocktakeSchemaMigration 自动完成等价的幂等迁移。
-- ====================================================================

USE buffer_block_db;

CREATE TABLE IF NOT EXISTS stocktake_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(50) NOT NULL UNIQUE COMMENT '盘点批次号 PD-yyyyMMdd-NNN',
    line_id BIGINT NOT NULL COMMENT '盘点产线ID',
    stocktake_date DATE NOT NULL COMMENT '盘点日期',
    operator VARCHAR(50) NOT NULL COMMENT '盘点负责人',
    remark VARCHAR(500) DEFAULT NULL COMMENT '批次备注',
    status VARCHAR(20) NOT NULL DEFAULT 'COUNTING' COMMENT '状态: COUNTING-盘点中, COMPLETED-已完成',
    total_count INT NOT NULL DEFAULT 0 COMMENT '应盘数量(快照)',
    counted_count INT NOT NULL DEFAULT 0 COMMENT '已盘数量(去重)',
    discrepancy_count INT NOT NULL DEFAULT 0 COMMENT '差异总数',
    pending_count INT NOT NULL DEFAULT 0 COMMENT '待处理差异数',
    finish_time DATETIME DEFAULT NULL COMMENT '封账时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_stocktake_batch_no (batch_no),
    INDEX idx_stocktake_line_id (line_id),
    INDEX idx_stocktake_date (stocktake_date),
    INDEX idx_stocktake_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块盘点批次';

CREATE TABLE IF NOT EXISTS stocktake_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL COMMENT '盘点批次ID',
    block_id BIGINT DEFAULT NULL COMMENT '挡块ID(账外编号可空)',
    block_code VARCHAR(50) DEFAULT NULL COMMENT '挡块编号快照',
    expected_line_id BIGINT DEFAULT NULL COMMENT '应盘产线ID',
    bound_line_id BIGINT DEFAULT NULL COMMENT '录入时当前绑定产线ID',
    site_line_id BIGINT DEFAULT NULL COMMENT '现场盘点产线ID',
    is_extra TINYINT NOT NULL DEFAULT 0 COMMENT '是否盘盈: 1-是, 0-否(应盘快照)',
    is_counted TINYINT NOT NULL DEFAULT 0 COMMENT '是否已盘: 1-是, 0-否',
    physical_status VARCHAR(20) DEFAULT NULL COMMENT '实物状态: NORMAL-正常, DAMAGED-损坏, SCRAPPED-报废',
    repeat_count INT NOT NULL DEFAULT 0 COMMENT '重复盘点次数(首盘为1)',
    count_operator VARCHAR(50) DEFAULT NULL COMMENT '盘点人',
    count_time DATETIME DEFAULT NULL COMMENT '盘点时间',
    count_remark VARCHAR(500) DEFAULT NULL COMMENT '现场备注',
    discrepancy_type VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '差异类型: NONE/MISSING/WRONG_LINE/DUPLICATE/EXTRA/DAMAGED/SCRAPPED',
    discrepancy_status VARCHAR(20) DEFAULT NULL COMMENT '处理状态: PENDING-待处理, CONFIRMED-已确认, IGNORED-已忽略',
    handle_operator VARCHAR(50) DEFAULT NULL COMMENT '差异处理人',
    handle_note VARCHAR(500) DEFAULT NULL COMMENT '处理说明',
    handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_stocktake_batch_block (batch_id, block_id),
    INDEX idx_stocktake_item_batch (batch_id),
    INDEX idx_stocktake_item_block (block_id),
    INDEX idx_stocktake_item_type (discrepancy_type),
    INDEX idx_stocktake_item_status (discrepancy_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块盘点明细与差异';

CREATE TABLE IF NOT EXISTS stocktake_sequence (
    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd',
    current_value INT NOT NULL COMMENT '已分配的最大流水号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点批次号按日期发号器';

INSERT IGNORE INTO stocktake_sequence (sequence_date, current_value) VALUES ('LOCK', 0);
