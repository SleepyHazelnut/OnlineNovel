package com.inkisle.service.impl;

import com.inkisle.mapper.AdminStatMapper;
import com.inkisle.service.AdminStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台首页统计
 * 未合并为单条 SQL：三张表分别查询按天数量，再在 Java 中按 day 合并，
 * 缺失日期补 0，前端得到的是对齐后的数据序列，可直接绘制
 */
@Service
public class AdminStatServiceImpl implements AdminStatService {

    @Autowired
    private AdminStatMapper adminStatMapper;

    @Override
    public Map<String, Object> total() {
        return adminStatMapper.selectTotal();
    }

    @Override
    public List<Map<String, Object>> daily(Integer days) {
        // 三张表分别按天查询数量，再按 day 合并；某表当日无数据时补 0，否则折线会断开
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        fill(merged, adminStatMapper.countUserByDay(days), "userNum");
        fill(merged, adminStatMapper.countBookByDay(days), "bookNum");
        fill(merged, adminStatMapper.countCommentByDay(days), "commentNum");
        return new ArrayList<>(merged.values());
    }

    private void fill(Map<String, Map<String, Object>> merged, List<Map<String, Object>> rows,
                      String key) {
        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("day"));
            Map<String, Object> item = merged.get(day);
            // 使用 LinkedHashMap 以保证插入顺序，从而保持日期有序，否则前端绘制顺序错乱
            if (item == null) {
                item = new HashMap<>();
                item.put("day", day);
                item.put("userNum", 0);
                item.put("bookNum", 0);
                item.put("commentNum", 0);
                merged.put(day, item);
            }
            item.put(key, row.get("num"));
        }
    }

}
