package com.inkisle.common;

import lombok.Data;

/**
 * 接口统一返回结果
 */
@Data
public class Result {

    /** 状态码，200 成功，500 失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private Object data;

    public static Result ok() {
        Result result = new Result();
        result.setCode(200);
        result.setMsg("操作成功");
        return result;
    }

    public static Result ok(Object data) {
        Result result = ok();
        result.setData(data);
        return result;
    }

    public static Result fail(String msg) {
        Result result = new Result();
        result.setCode(500);
        result.setMsg(msg);
        return result;
    }

}
