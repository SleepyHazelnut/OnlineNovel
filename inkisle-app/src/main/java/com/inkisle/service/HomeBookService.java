package com.inkisle.service;

import com.inkisle.dto.HomeBookDTO;

import java.util.List;
import java.util.Map;

/**
 * 热门、精品由管理员配；最新更新不占推荐位，按更新时间实时取
 */
public interface HomeBookService {

    List<Map<String, Object>> listBySection(Integer section);

    Map<String, Object> recommend();

    void add(HomeBookDTO dto);

    void updateSort(Long id, Integer sort);

    void delete(Long id);

}
