-- ====================================================================
-- V7：挡块班次点检台账（按产线筛选在用挡块，班次点检打卡）
-- 幂等设计：CREATE TABLE IF NOT EXISTS，可重复执行
-- 应用启动时 InspectionSchemaMigration 会自动执行同等迁移；
-- /docker-entrypoint-initdb.d 下脚本仅在 MySQL 数据目录为空时执行，
-- 存量数据卷升级依赖应用启动迁移，本脚本供手动初始化/核对使用。
-- ====================================================================

USE buffer_block_db;

CREATE TABLE IF NOT EXISTS block_inspection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    block_id BIGINT NOT NULL COMMENT '挡块ID',
    inspection_time DATETIME NOT NULL COMMENT '点检打卡时刻',
    shift_code VARCHAR(20) NOT NULL COMMENT '点检班次: MORNING-早班, AFTERNOON-中班, NIGHT-晚班',
    inspector VARCHAR(50) NOT NULL COMMENT '点检人',
    result VARCHAR(20) NOT NULL COMMENT '点检结论: USABLE-可用, UNUSABLE-不可用',
    note VARCHAR(500) DEFAULT NULL COMMENT '点检备注/不可用情况说明',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_insp_block (block_id),
    INDEX idx_insp_time (inspection_time),
    INDEX idx_insp_result (result),
    INDEX idx_insp_shift (shift_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挡块班次点检记录';
