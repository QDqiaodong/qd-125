package com.bufferblock.dto;

public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public Result() {
    }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    /** 点检提交被待复检挡块拦截：专用错误码 + 结构化明细（条数/编号清单） */
    public static <T> Result<T> inspectionPendingRecheck(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(4091);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}
