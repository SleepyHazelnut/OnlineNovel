package com.inkisle.dto;

/**
 * 一次签到的结果
 * 只回 boolean 的话，「今天签过了」和「签上了但因为别的原因没给分」分不出来，
 * 前端只能靠猜
 */
public record SignResult(boolean repeat, int points) {

    public static SignResult repeated() {
        return new SignResult(true, 0);
    }

    public static SignResult signed(int points) {
        return new SignResult(false, points);
    }

}
