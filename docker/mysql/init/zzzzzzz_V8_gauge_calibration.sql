-- ====================================================================
-- V8：点检工装校准台（卡尺/塞尺/百分表台账 + 工装校准记录）
-- 幂等设计：CREATE TABLE IF NOT EXISTS，可重复执行
-- 应用启动时 GaugeCalibrationSchemaMigration 会自动执行同等迁移；
-- /docker-entrypoint-initdb.d 下脚本仅在 MySQL 数据目录为空时执行，
-- 存量数据卷升级依赖应用启动迁移，本脚本供手动初始化/核对使用。
-- ====================================================================

USE buffer_block_db;

CREATE TABLE IF NOT EXISTS gauge_tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_code VARCHAR(50) NOT NULL COMMENT '工装编号',
    tool_type VARCHAR(30) NOT NULL COMMENT '工装类型: CALIPER-卡尺, FEELER-塞尺, DIAL_INDICATOR-百分表',
    spec_model VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
    calibration_due_date DATE NOT NULL COMMENT '校准到期日（合格校准时按下次应校日同步）',
    keeper_team VARCHAR(100) NOT NULL COMMENT '保管班组',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    disabled TINYINT NOT NULL DEFAULT 0 COMMENT '停用标记: 0-在期, 1-停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_gauge_code (tool_code),
    INDEX idx_gauge_due (calibration_due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点检工装台账（卡尺/塞尺/百分表）';

CREATE TABLE IF NOT EXISTS gauge_calibration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id BIGINT NOT NULL COMMENT '工装ID',
    calibration_date DATE NOT NULL COMMENT '校准日期',
    result VARCHAR(20) NOT NULL COMMENT '校准结论: PASS-合格, FAIL-不合格',
    valid_until DATE NOT NULL COMMENT '本次校准有效期至',
    next_due_date DATE NOT NULL COMMENT '下次应校日期',
    calibrator VARCHAR(50) NOT NULL COMMENT '校准人',
    note VARCHAR(500) DEFAULT NULL COMMENT '校准备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gauge_cal_tool (tool_id),
    INDEX idx_gauge_cal_date (calibration_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点检工装校准记录';
