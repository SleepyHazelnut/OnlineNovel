package com.inkisle.common;

/**
 * 统一业务状态码
 * 将 200、401、500 等状态码集中到枚举中，使用时通过 `ResultCode.XXX` 引用，
 * 避免散落的魔法数字，同时语义更清晰
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "没有访问权限"),
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
