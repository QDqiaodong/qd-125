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
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_block_code (block_code),
    INDEX idx_spec_template (spec_template)
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
