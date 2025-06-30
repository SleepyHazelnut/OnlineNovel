package com.inkisle.service;

import com.inkisle.dto.HomeBookDTO;

import java.util.List;
import java.util.Map;

/**
 * 首页推荐位
 * 热门推荐、精品推荐由管理员配置；最新更新不占推荐位，按最近更新时间实时取，
 * 否则每更一章都要管理员手动挪一次
 */
public interface HomeBookService {

    /**
     * 管理端：某个板块的全部条目，包含已下架、已驳回的书
     */
    List<Map<String, Object>> listBySection(Integer section);

    /**
     * 读者端：三个板块的内容。推荐位只返回已上架作品
     */
    Map<String, Object> recommend();

    /**
     * 加入推荐位。同一本书在同一板块只能出现一次
     */
    void add(HomeBookDTO dto);

    /**
     * 调整排序，值越大越靠前
     */
    void updateSort(Long id, Integer sort);

    void delete(Long id);

}
