package com.inkisle.common;

import lombok.Data;

/**
 * 接口统一返回结果
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

    private static <T> Result<T> build(ResultCode resultCode, T data) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMsg(resultCode.getMsg());
        result.setData(data);
        return result;
    }

}
