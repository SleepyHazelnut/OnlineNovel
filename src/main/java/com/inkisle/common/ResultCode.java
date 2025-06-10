package com.inkisle.common;

/**
 * 统一业务状态码
 * 之前 200 / 500 这些数字散在各处，改起来容易漏，收拢到一个枚举里
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "请先登录"),
    NOT_FOUND(404, "资源不存在"),
    FAIL(500, "操作失败");

    private final Integer code;
    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

}
