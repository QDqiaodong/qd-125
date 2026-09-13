package com.bufferblock.exception;

/**
 * 点检提交被“本产线该班次存在尚未复检通过的不可用挡块”拦截时抛出，
 * 携带结构化的待复检挡块明细（条数 + 编号清单），前端弹窗逐条列出，而非笼统失败提示。
 */
public class InspectionPendingRecheckException extends RuntimeException {

    private final transient Object detail;

    public InspectionPendingRecheckException(String message, Object detail) {
        super(message);
        this.detail = detail;
    }

    public Object getDetail() {
        return detail;
    }
}
