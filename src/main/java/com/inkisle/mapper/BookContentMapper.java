package com.inkisle.mapper;

import com.inkisle.entity.BookContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookContentMapper {

    /**
     * 按章节 ID 查询正文。正文单独建表，避免大字段拖慢章节列表查询
     */
    BookContent selectByChapterId(@Param("chapterId") Long chapterId);

    int insert(BookContent content);

    // 正文与章节为一对一关系，chapter_id 即可唯一定位，故相关方法均以 chapterId 为条件
    int updateByChapterId(BookContent content);

    int deleteByChapterId(@Param("chapterId") Long chapterId);

}
