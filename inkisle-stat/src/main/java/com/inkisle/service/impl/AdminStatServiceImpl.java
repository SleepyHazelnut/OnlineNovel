package com.inkisle.service.impl;

import com.inkisle.mapper.AdminStatMapper;
import com.inkisle.service.AdminStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 后台首页统计。三张表分开按天查，在 Java 里按日期合起来，缺的那天补 0
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
        // 三张表分开按天查再合并，某张表这天没数据就补 0，不然折线会断开
        // 用 TreeMap 不用 LinkedHashMap：三个结果各自是有序的，交错插进来就乱了
        // （用户表是 1 号 5 号，书籍表只有 3 号，合出来是 1,5,3）
        // 日期是 yyyy-MM-dd，字符串序就是时间序，交给 TreeMap 排
        Map<String, Map<String, Object>> merged = new TreeMap<>();
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
