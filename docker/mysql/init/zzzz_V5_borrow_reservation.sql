-- ====================================================================
-- V5：缓冲挡块借用预约（存量库迁移，幂等可重复执行）
-- 全新部署时 schema.sql 已包含全部结构，本脚本执行结果为空操作；
-- 存量部署无需手动执行本脚本——新版后端启动时会通过
-- BorrowReservationSchemaMigration 自动完成等价的幂等迁移。
--
-- 与移交划转相互独立：借用不改产线归属，只在占用期间于挡块档案上
-- 叠加“已约出/已取走”标记；预约取消必须登记原因。
-- ====================================================================

USE buffer_block_db;

CREATE TABLE IF NOT EXISTS block_borrow_reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_no VARCHAR(50) NOT NULL UNIQUE COMMENT '预约单号 BR-yyyyMMdd-NNN',
    block_id BIGINT NOT NULL COMMENT '挡块ID',
    team_name VARCHAR(100) NOT NULL COMMENT '借用班组',
    pickup_time DATETIME NOT NULL COMMENT '约定取用时间',
    planned_return_time DATETIME DEFAULT NULL COMMENT '计划归还时间',
    return_point VARCHAR(200) NOT NULL COMMENT '归还点',
    contact_person VARCHAR(50) DEFAULT NULL COMMENT '班组联系人',
    contact_phone VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
    purpose VARCHAR(500) DEFAULT NULL COMMENT '借用用途',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED' COMMENT '状态: RESERVED-已预约, PICKED_UP-已取走, RETURNED-已归还, CANCELLED-已取消, OVERDUE-逾时未取',
    pickup_operator VARCHAR(50) DEFAULT NULL COMMENT '取走登记人',
    actual_pickup_time DATETIME DEFAULT NULL COMMENT '实际取走时间',
    return_operator VARCHAR(50) DEFAULT NULL COMMENT '归还登记人',
    actual_return_time DATETIME DEFAULT NULL COMMENT '实际归还时间',
    cancel_reason VARCHAR(500) DEFAULT NULL COMMENT '取消原因(必填)',
    cancel_operator VARCHAR(50) DEFAULT NULL COMMENT '取消操作人',
    cancel_time DATETIME DEFAULT NULL COMMENT '取消时间',
    remind_count INT NOT NULL DEFAULT 0 COMMENT '逾时未取提醒次数',
    last_remind_time DATETIME DEFAULT NULL COMMENT '最近一次提醒时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_borrow_no (reservation_no),
    INDEX idx_borrow_block (block_id),
    INDEX idx_borrow_team (team_name),
    INDEX idx_borrow_pickup (pickup_time),
    INDEX idx_borrow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缓冲挡块借用预约';

CREATE TABLE IF NOT EXISTS block_borrow_sequence (
    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd',
    current_value INT NOT NULL COMMENT '已分配的最大流水号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='借用预约单号按日期发号器';

INSERT IGNORE INTO block_borrow_sequence (sequence_date, current_value) VALUES ('LOCK', 0);

CREATE TABLE IF NOT EXISTS block_borrow_flow_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL COMMENT '预约单ID',
    action VARCHAR(30) NOT NULL COMMENT '动作: BOOK-预约, PICKUP-取走, RETURN-归还, CANCEL-取消, OVERDUE_REMIND-逾时提醒',
    from_status VARCHAR(20) DEFAULT NULL COMMENT '变更前状态',
    to_status VARCHAR(20) DEFAULT NULL COMMENT '变更后状态',
    operator VARCHAR(50) DEFAULT NULL COMMENT '操作人',
    note VARCHAR(500) DEFAULT NULL COMMENT '处理说明/原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_borrow_flow_reservation (reservation_id),
    INDEX idx_borrow_flow_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='借用预约流转记录';
