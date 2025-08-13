package com.inkisle.common;

import lombok.Data;

/**
 * 接口统一返回 code + msg + data
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
     * 错误码从 ResultCode 里取，提示信息换成更具体的，比如参数校验失败时指出是哪一项
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
