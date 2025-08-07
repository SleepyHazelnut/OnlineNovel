package com.inkisle.service;

import java.util.List;
import java.util.Map;

public interface AdminStatService {

    Map<String, Object> total();

    /** 按天新增，缺失日期补零，前端直接画图 */
    List<Map<String, Object>> daily(Integer days);

}
