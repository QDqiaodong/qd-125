package com.bufferblock.exception;

/**
 * 班组交班中，接班人确认“拦截中点检工装”事项时，该工装仍在校准台拦截清单
 * （到期未校准 / 校准结论不合格）而拒绝确认时抛出。
 * 携带结构化的工装明细（编号/类型/保管班组/拦截原因），前端弹窗逐条展示，
 * 而不是只给一句笼统失败提示。
 */
public class HandoverGaugeStillBlockedException extends RuntimeException {

    private final transient Object detail;

    public HandoverGaugeStillBlockedException(String message, Object detail) {
        super(message);
        this.detail = detail;
    }

    public Object getDetail() {
        return detail;
    }
}
