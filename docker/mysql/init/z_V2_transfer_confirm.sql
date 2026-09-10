-- ====================================================================
-- 移交确认业务模块升级脚本（存量库迁移，幂等可重复执行）
-- 全新部署时 schema.sql 已包含全部结构，本脚本执行结果为空操作；
-- 存量部署可手动执行：mysql -uroot -p buffer_block_db < z_V2_transfer_confirm.sql
--
-- 说明：/docker-entrypoint-initdb.d 仅在 MySQL 数据目录为空时执行，
--       存量数据卷升级无需手动执行本脚本——新版后端启动时会通过
--       TransferConfirmSchemaMigration 自动完成等价的幂等迁移。
-- ====================================================================

USE buffer_block_db;

-- --------------------------------------------------------------------
-- block_transfer 新增状态/处理/回执字段（information_schema 守卫，列已存在则跳过）
-- --------------------------------------------------------------------
SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'buffer_block_db' AND TABLE_NAME = 'block_transfer'
       AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE block_transfer ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''状态: PENDING-待确认, CONFIRMED-已确认, REJECTED-已驳回'' AFTER remark',
    'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'buffer_block_db' AND TABLE_NAME = 'block_transfer'
       AND COLUMN_NAME = 'handle_note') = 0,
    'ALTER TABLE block_transfer ADD COLUMN handle_note VARCHAR(500) DEFAULT NULL COMMENT ''接收方处理说明'' AFTER status',
    'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'buffer_block_db' AND TABLE_NAME = 'block_transfer'
       AND COLUMN_NAME = 'handle_time') = 0,
    'ALTER TABLE block_transfer ADD COLUMN handle_time DATETIME DEFAULT NULL COMMENT ''接收方处理时间'' AFTER handle_note',
    'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'buffer_block_db' AND TABLE_NAME = 'block_transfer'
       AND COLUMN_NAME = 'receipt_print_count') = 0,
    'ALTER TABLE block_transfer ADD COLUMN receipt_print_count INT DEFAULT 0 COMMENT ''确认回执打印次数'' AFTER last_print_time',
    'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'buffer_block_db' AND TABLE_NAME = 'block_transfer'
       AND COLUMN_NAME = 'last_receipt_print_time') = 0,
    'ALTER TABLE block_transfer ADD COLUMN last_receipt_print_time DATETIME DEFAULT NULL COMMENT ''回执最后打印时间'' AFTER receipt_print_count',
    'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 状态索引（MySQL 8 存储过程幂等建索引）
DROP PROCEDURE IF EXISTS idx_block_transfer_status;
DELIMITER //
CREATE PROCEDURE idx_block_transfer_status()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = 'buffer_block_db' AND TABLE_NAME = 'block_transfer'
                     AND INDEX_NAME = 'idx_status') THEN
        ALTER TABLE block_transfer ADD INDEX idx_status (status);
    END IF;
END //
DELIMITER ;
CALL idx_block_transfer_status();
DROP PROCEDURE IF EXISTS idx_block_transfer_status;

-- --------------------------------------------------------------------
-- 流转记录表（需先于下面的历史数据回填创建，回填通过 REGISTER 记录
--   区分“历史单据”与“新流程待确认单据”）
-- --------------------------------------------------------------------
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

-- --------------------------------------------------------------------
-- 历史移交单：升级前没有流转记录表，旧流程登记即完成产线绑定。
-- 仅回填“尚无 REGISTER 流转记录”的历史单据为已确认；
-- 新流程已登记、待接收方处理的单据（已有 REGISTER 记录）保持 PENDING，
-- 不会被错误地置为已确认。
-- （全新部署时种子数据自带 CONFIRMED/REJECTED 状态，此处影响 0 行）
-- --------------------------------------------------------------------
UPDATE block_transfer t
SET t.status = 'CONFIRMED',
    t.handle_note = COALESCE(t.handle_note, CONCAT('历史数据迁移：原流程已完成移交', IFNULL(CONCAT('（', t.remark, '）'), ''))),
    t.handle_time = COALESCE(t.handle_time, t.update_time)
WHERE (t.status IS NULL OR t.status = '' OR t.status = 'PENDING')
  AND NOT EXISTS (SELECT 1 FROM transfer_flow_record r
                  WHERE r.transfer_id = t.id AND r.action = 'REGISTER');

-- 兜底：任何遗漏的空状态旧记录均视为已确认，避免错误地出现在待确认列表
UPDATE block_transfer SET status = 'CONFIRMED'
WHERE status IS NULL OR status = '';

-- --------------------------------------------------------------------
-- 为历史移交单补登流转记录（按 transfer + action 幂等，可重复执行）
-- --------------------------------------------------------------------
INSERT INTO transfer_flow_record (transfer_id, action, from_status, to_status, operator, note, create_time)
SELECT t.id, 'REGISTER', NULL, 'PENDING', t.transfer_operator, '移交登记，等待接收方确认', t.create_time
FROM block_transfer t
WHERE NOT EXISTS (SELECT 1 FROM transfer_flow_record r WHERE r.transfer_id = t.id AND r.action = 'REGISTER');

INSERT INTO transfer_flow_record (transfer_id, action, from_status, to_status, operator, note, create_time)
SELECT t.id, 'CONFIRM', 'PENDING', 'CONFIRMED',
       COALESCE(NULLIF(t.receive_operator, ''), t.transfer_operator),
       COALESCE(t.handle_note, '历史数据迁移：确认接收'), t.handle_time
FROM block_transfer t
WHERE t.status = 'CONFIRMED'
  AND NOT EXISTS (SELECT 1 FROM transfer_flow_record r WHERE r.transfer_id = t.id AND r.action = 'CONFIRM');

INSERT INTO transfer_flow_record (transfer_id, action, from_status, to_status, operator, note, create_time)
SELECT t.id, 'REJECT', 'PENDING', 'REJECTED',
       COALESCE(NULLIF(t.receive_operator, ''), t.transfer_operator),
       COALESCE(t.handle_note, '历史数据迁移：驳回'), t.handle_time
FROM block_transfer t
WHERE t.status = 'REJECTED'
  AND NOT EXISTS (SELECT 1 FROM transfer_flow_record r WHERE r.transfer_id = t.id AND r.action = 'REJECT');
