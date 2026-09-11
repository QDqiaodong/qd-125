-- ====================================================================
-- 移交单号持久化按日期发号器升级脚本（V3，幂等可重复执行）
-- 替代后端进程内计数：应用重启或多实例并发时，由数据库行锁保证单号稳定唯一。
-- 新版后端启动时也会通过 TransferSequenceSchemaMigration 自动完成等价迁移。
-- ====================================================================

USE buffer_block_db;

CREATE TABLE IF NOT EXISTS block_transfer_sequence (
    sequence_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '发号业务日期 yyyyMMdd',
    current_value INT NOT NULL COMMENT '已分配的最大流水号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='移交单号按日期发号器';

INSERT IGNORE INTO block_transfer_sequence (sequence_date, current_value) VALUES ('LOCK', 0);
