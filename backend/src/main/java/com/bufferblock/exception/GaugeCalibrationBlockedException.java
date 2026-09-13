package com.bufferblock.exception;

/**
 * 班次点检打卡被“到期未校准或校准结论不合格的工装”拦截时抛出，
 * 携带结构化的超期工装明细（条数 + 编号清单），前端弹窗逐条列出工装编号，而非笼统失败提示。
 */
public class GaugeCalibrationBlockedException extends RuntimeException {

    private final transient Object detail;

    public GaugeCalibrationBlockedException(String message, Object detail) {
        super(message);
        this.detail = detail;
    }

    public Object getDetail() {
        return detail;
    }
}
