package com.inkisle.mapper;

import com.inkisle.entity.BookCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookCategoryMapper {

    /**
     * 全部分类，按 sort 升序。分类是初始化时一次性写进去的，基本不动，
     * 就不分页也不加过滤了
     */
    List<BookCategory> selectAll();

    BookCategory selectById(@Param("id") Long id);

    /**
     * 同级重名检查。excludeId 用于修改时排除自身，新增时传 null
     */
    int countByName(@Param("categoryName") String categoryName,
                    @Param("parentId") Long parentId,
                    @Param("excludeId") Long excludeId);

    int countChildren(@Param("parentId") Long parentId);

    int insert(BookCategory category);

    int update(BookCategory category);

    int deleteById(@Param("id") Long id);

}
