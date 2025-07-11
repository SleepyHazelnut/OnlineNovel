package com.inkisle.common;

import lombok.Data;

/**
 * 接口返回结构：code + msg + data
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** 状态码，见 ResultCode */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    public static <T> Result<T> ok() {
        return build(ResultCode.SUCCESS, null);
    }

    public static <T> Result<T> ok(T data) {
        return build(ResultCode.SUCCESS, data);
    }

    public static <T> Result<T> fail(String msg) {
        Result<T> result = build(ResultCode.FAIL, null);
        result.setMsg(msg);
        return result;
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return build(resultCode, null);
    }

    /**
     * 错误码取自 `ResultCode`，提示信息替换为更具体的内容，例如参数校验失败时指明具体项
     */
    public static <T> Result<T> fail(ResultCode resultCode, String msg) {
        Result<T> result = build(resultCode, null);
        result.setMsg(msg);
        return result;
    }

    private static <T> Result<T> build(ResultCode resultCode, T data) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMsg(resultCode.getMsg());
        result.setData(data);
        return result;
    }

}
