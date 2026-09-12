-- ====================================================================
-- 输送设备缓冲挡块跨产线移交划转系统 - 数据库初始化脚本
-- 幂等设计：CREATE TABLE IF NOT EXISTS + INSERT IGNORE
-- 即使旧数据卷重复挂载或脚本被重复执行，也不会因主键冲突导致启动失败
-- ====================================================================

CREATE DATABASE IF NOT EXISTS buffer_block_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE buffer_block_db;

CREATE TABLE IF NOT EXISTS production_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    line_code VARCHAR(50) NOT NULL UNIQUE COMMENT '产线编号',
    line_name VARCHAR(100) NOT NULL COMMENT '产线名称',
    parent_id BIGINT DEFAULT NULL COMMENT '父产线ID',
    workshop VARCHAR(100) DEFAULT NULL COMMENT '所属车间',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id),
    INDEX idx_line_code (line_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产线表';

CREATE TABLE IF NOT EXISTS buffer_block (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    block_code VARCHAR(50) NOT NULL UNIQUE COMMENT '挡块编号',
    adapter_model VARCHAR(100) NOT NULL COMMENT '适配输送机型',
    thickness DECIMAL(10,2) NOT NULL COMMENT '厚度规格(mm)',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '实物图片URL',
    spec_template VARCHAR(50) DEFAULT NULL COMMENT '规格模板标识',
    calibration_cycle_months INT DEFAULT 12 COMMENT '校准周期(月)',
    service_status VARCHAR(20) DEFAULT 'IN_SERVICE' COMMENT '在用状态: IN_SERVICE-在用, SUSPENDED-挂起待修',
    suspend_reason VARCHAR(500) DEFAULT NULL COMMENT '挂起原因',
    suspend_time DATETIME DEFAULT NULL COMMENT '挂起时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_block_code (block_code),
    INDEX idx_spec_template (spec_template),
    INDEX idx_service_status (service_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缓冲挡块基础档案';

CREATE TABLE IF NOT EXISTS block_line_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    block_id BIGINT NOT NULL COMMENT '挡块ID',
    line_id BIGINT NOT NULL COMMENT '产线ID',
    bind_type TINYINT NOT NULL DEFAULT 1 COMMENT '绑定类型: 1-初始绑定, 2-移交后绑定',
    bind_time DATETIME NOT NULL COMMENT '绑定时间',
    operator VARCHAR(50) DEFAULT NULL COMMENT '操作人',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    is_current TINYINT DEFAULT 1 COMMENT '是否当前归属: 1-是, 0-否',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_block_id (block_id),
    INDEX idx_line_id (line_id),
    INDEX idx_is_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块产线绑定记录';

CREATE TABLE IF NOT EXISTS block_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_no VARCHAR(50) NOT NULL UNIQUE COMMENT '移交单号',
    block_id BIGINT NOT NULL COMMENT '挡块ID',
    from_line_id BIGINT NOT NULL COMMENT '移出产线ID',
    to_line_id BIGINT NOT NULL COMMENT '移入产线ID',
    transfer_date DATE NOT NULL COMMENT '移交日期',
    transfer_reason VARCHAR(500) DEFAULT NULL COMMENT '移交原因',
    transfer_operator VARCHAR(50) NOT NULL COMMENT '移交操作人',
    receive_operator VARCHAR(50) DEFAULT NULL COMMENT '接收人',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待确认, CONFIRMED-已确认, REJECTED-已驳回',
    handle_note VARCHAR(500) DEFAULT NULL COMMENT '接收方处理说明',
    handle_time DATETIME DEFAULT NULL COMMENT '接收方处理时间',
    print_count INT DEFAULT 0 COMMENT '移交单打印次数',
    last_print_time DATETIME DEFAULT NULL COMMENT '最后打印时间',
    receipt_print_count INT DEFAULT 0 COMMENT '确认回执打印次数',
    last_receipt_print_time DATETIME DEFAULT NULL COMMENT '回执最后打印时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_transfer_no (transfer_no),
    INDEX idx_block_id (block_id),
    INDEX idx_transfer_date (transfer_date),
    INDEX idx_from_line (from_line_id),
    INDEX idx_to_line (to_line_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块跨产线移交记录';

CREATE TABLE IF NOT EXISTS block_transfer_sequence (
    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd',
    current_value INT NOT NULL COMMENT '已分配的最大流水号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='移交单号按日期发号器';

INSERT IGNORE INTO block_transfer_sequence (sequence_date, current_value) VALUES ('LOCK', 0);

CREATE TABLE IF NOT EXISTS transfer_flow_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id BIGINT NOT NULL COMMENT '移交单ID',
    action VARCHAR(30) NOT NULL COMMENT '动作: REGISTER-登记, CONFIRM-确认, REJECT-驳回, PRINT_RECEIPT-打印回执, PRINT_ORDER-打印移交单',
    from_status VARCHAR(20) DEFAULT NULL COMMENT '变更前状态',
    to_status VARCHAR(20) DEFAULT NULL COMMENT '变更后状态',
    operator VARCHAR(50) DEFAULT NULL COMMENT '操作人',
    note VARCHAR(500) DEFAULT NULL COMMENT '处理说明/备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transfer_id (transfer_id),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='移交确认流转记录';

-- ====================================================================
-- V4：挡块盘点差异闭环
-- ====================================================================

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

-- 幂等种子数据：INSERT IGNORE 避免主键/唯一键冲突
INSERT IGNORE INTO production_line (id, line_code, line_name, parent_id, workshop, sort_order) VALUES
(1, 'WS-A', 'A车间', NULL, 'A车间', 1),
(2, 'WS-A-L01', 'A车间-01号线', 1, 'A车间', 1),
(3, 'WS-A-L02', 'A车间-02号线', 1, 'A车间', 2),
(4, 'WS-A-L03', 'A车间-03号线', 1, 'A车间', 3),
(5, 'WS-B', 'B车间', NULL, 'B车间', 2),
(6, 'WS-B-L01', 'B车间-01号线', 5, 'B车间', 1),
(7, 'WS-B-L02', 'B车间-02号线', 5, 'B车间', 2),
(8, 'WS-B-L03', 'B车间-03号线', 5, 'B车间', 3),
(9, 'WS-C', 'C车间', NULL, 'C车间', 3),
(10, 'WS-C-L01', 'C车间-01号线', 9, 'C车间', 1),
(11, 'WS-C-L02', 'C车间-02号线', 9, 'C车间', 2);

INSERT IGNORE INTO buffer_block (id, block_code, adapter_model, thickness, spec_template) VALUES
(1, 'BLK-2024-001', 'SSJ-3000A型输送机', 50.00, 'TEMP-A-50'),
(2, 'BLK-2024-002', 'SSJ-3000A型输送机', 50.00, 'TEMP-A-50'),
(3, 'BLK-2024-003', 'SSJ-3000A型输送机', 60.00, 'TEMP-A-60'),
(4, 'BLK-2024-004', 'SSJ-4000B型输送机', 80.00, 'TEMP-B-80'),
(5, 'BLK-2024-005', 'SSJ-4000B型输送机', 80.00, 'TEMP-B-80'),
(6, 'BLK-2024-006', 'SSJ-5000C型输送机', 100.00, 'TEMP-C-100');

INSERT IGNORE INTO block_line_binding (id, block_id, line_id, bind_type, bind_time, operator, remark, is_current) VALUES
(1, 1, 2, 1, '2024-01-15 08:30:00', '张工', '初始分配', 1),
(2, 2, 3, 1, '2024-01-15 08:35:00', '张工', '初始分配', 1),
(3, 3, 4, 1, '2024-01-20 09:00:00', '李工', '初始分配', 1),
(4, 4, 6, 1, '2024-02-01 10:00:00', '王工', '初始分配', 1),
(5, 5, 7, 1, '2024-02-01 10:15:00', '王工', '初始分配', 1),
(6, 6, 10, 1, '2024-03-01 14:00:00', '赵工', '初始分配', 1);

INSERT IGNORE INTO block_transfer (id, transfer_no, block_id, from_line_id, to_line_id, transfer_date, transfer_reason, transfer_operator, receive_operator, remark, status, handle_note, handle_time, print_count) VALUES
(1, 'TRF-20240601-001', 1, 2, 6, '2024-06-01', '车间产线改造，调整配置', '张工', '刘工', 'A车间升级改造移交', 'CONFIRMED', '实物核对无误，同意接收', '2024-06-01 15:30:00', 1),
(2, 'TRF-20240605-002', 2, 3, 10, '2024-06-05', '临时调配支持C车间', '李工', '陈工', '临时借用', 'CONFIRMED', '已接收并安装就位', '2024-06-05 10:20:00', 0),
(3, 'TRF-20240610-003', 4, 6, 2, '2024-06-10', '改造完成回迁', '刘工', '张工', 'A车间改造完成回迁', 'REJECTED', '挡块厚度规格与01号线当前机型不匹配，暂不接收', '2024-06-10 14:10:00', 0);

-- 历史移交单的流转记录（幂等：按主键 IGNORE）
INSERT IGNORE INTO transfer_flow_record (id, transfer_id, action, from_status, to_status, operator, note, create_time) VALUES
(1, 1, 'REGISTER', NULL, 'PENDING', '张工', '移交登记，等待接收方确认', '2024-06-01 09:00:00'),
(2, 1, 'CONFIRM', 'PENDING', 'CONFIRMED', '刘工', '实物核对无误，同意接收', '2024-06-01 15:30:00'),
(3, 1, 'PRINT_ORDER', 'CONFIRMED', 'CONFIRMED', '张工', '打印移交单', '2024-06-02 08:30:00'),
(4, 2, 'REGISTER', NULL, 'PENDING', '李工', '移交登记，等待接收方确认', '2024-06-05 08:50:00'),
(5, 2, 'CONFIRM', 'PENDING', 'CONFIRMED', '陈工', '已接收并安装就位', '2024-06-05 10:20:00'),
(6, 3, 'REGISTER', NULL, 'PENDING', '刘工', '移交登记，等待接收方确认', '2024-06-10 11:00:00'),
(7, 3, 'REJECT', 'PENDING', 'REJECTED', '张工', '挡块厚度规格与01号线当前机型不匹配，暂不接收', '2024-06-10 14:10:00');

-- ====================================================================
-- V5：缓冲挡块借用预约（独立于移交划转，借用期间不改产线归属）
-- ====================================================================

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
    actual_return_point VARCHAR(200) DEFAULT NULL COMMENT '实际归还点（归还时可改，留空沿用约定归还点）',
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

-- ====================================================================
-- V6：班组交班（一次性登记未还预约/待确认移交/待处理盘点差异，接班人逐条确认）
-- ====================================================================

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
