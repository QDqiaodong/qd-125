-- ====================================================================
-- V6：班组交班（存量库迁移，幂等可重复执行）
-- 全新部署时 schema.sql 已包含全部结构，本脚本执行结果为空操作；
-- 存量部署无需手动执行本脚本——新版后端启动时会通过
-- ShiftHandoverSchemaMigration 自动完成等价的幂等迁移。
--
-- 交班时一次性快照登记当班未还预约、待确认移交与待处理盘点差异，
-- 接班人逐条确认后交班才完成；交班未完成期间禁止新开借用预约。
-- ====================================================================

USE buffer_block_db;

CREATE TABLE IF NOT EXISTS shift_handover (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    handover_no VARCHAR(50) NOT NULL UNIQUE COMMENT '交班单号 HO-yyyyMMdd-NNN',
    from_team VARCHAR(100) NOT NULL COMMENT '交班班组',
    to_team VARCHAR(100) NOT NULL COMMENT '接班班组',
    handover_operator VARCHAR(50) NOT NULL COMMENT '交班登记人',
    receive_operator VARCHAR(50) NOT NULL COMMENT '接班人',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态: IN_PROGRESS-交班中, COMPLETED-已完成',
    total_count INT NOT NULL DEFAULT 0 COMMENT '交班事项总条数(登记时快照)',
    confirmed_count INT NOT NULL DEFAULT 0 COMMENT '接班人已确认条数',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    finish_time DATETIME DEFAULT NULL COMMENT '交班完成时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_handover_no (handover_no),
    INDEX idx_handover_status (status),
    INDEX idx_handover_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班组交班主表';

CREATE TABLE IF NOT EXISTS shift_handover_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    handover_id BIGINT NOT NULL COMMENT '交班单ID',
    item_type VARCHAR(30) NOT NULL COMMENT '事项类型: BORROW_UNRETURNED-未还预约, TRANSFER_PENDING-待确认移交, STOCKTAKE_PENDING-待处理盘点差异',
    ref_id BIGINT DEFAULT NULL COMMENT '源单据ID快照(预约单/移交单/盘点明细)',
    ref_no VARCHAR(50) DEFAULT NULL COMMENT '源单号快照',
    block_id BIGINT DEFAULT NULL COMMENT '挡块ID快照',
    block_code VARCHAR(50) DEFAULT NULL COMMENT '挡块编号快照',
    summary VARCHAR(500) DEFAULT NULL COMMENT '事项摘要快照',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待确认, CONFIRMED-已确认',
    confirm_operator VARCHAR(50) DEFAULT NULL COMMENT '确认人(接班人)',
    confirm_time DATETIME DEFAULT NULL COMMENT '确认时间',
    confirm_note VARCHAR(500) DEFAULT NULL COMMENT '确认备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_handover_item_handover (handover_id),
    INDEX idx_handover_item_status (status),
    INDEX idx_handover_item_type (item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班组交班事项';

CREATE TABLE IF NOT EXISTS shift_handover_sequence (
    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd',
    current_value INT NOT NULL COMMENT '已分配的最大流水号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班组交班单号按日期发号器';

INSERT IGNORE INTO shift_handover_sequence (sequence_date, current_value) VALUES ('LOCK', 0);
