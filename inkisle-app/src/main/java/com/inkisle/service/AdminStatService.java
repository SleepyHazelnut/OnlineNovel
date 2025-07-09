package com.inkisle.service;

import java.util.List;
import java.util.Map;

public interface AdminStatService {

    /**
     * 总量统计：用户数、书籍数、评论数等汇总概览
     */
    Map<String, Object> total();

    /**
     * 最近数天的按天趋势，`days` 为向前回溯的天数
     */
    List<Map<String, Object>> daily(Integer days);

}
